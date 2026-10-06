#!/usr/bin/env -S uv run --script
# /// script
# requires-python = ">=3.12"
# dependencies = []
# ///
"""Specify deployment, owned cleanup and current consumer preparation without a launch."""

import importlib.util
import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest import mock
import zipfile


# Importing the harness must not leave bytecode outside task-owned target output.
sys.dont_write_bytecode = True
MODULE_SPEC = importlib.util.spec_from_file_location(
    'phase65_live_consumer', Path(__file__).with_name('run-cbd-consumer.py'))
LIVE = importlib.util.module_from_spec(MODULE_SPEC)
MODULE_SPEC.loader.exec_module(LIVE)
TARGET = LIVE.safe_path(Path(__file__).resolve().parents[3] / 'target/phase65')
IDENTITY = {'namespace': 'org.simplemodeling.textus', 'id': 'CbdSupport',
            'version': '0.1.0-SNAPSHOT'}
AUTHENTICATION = {
    'convention': 'disabled', 'fallback_privilege': 'disabled',
    'local_subject': {'id': 'cbd-standalone-review', 'roles': ['reviewer'],
                      'security_level': 'user',
                      'attributes': {'installation': 'cbd-standalone'}},
}


def canonical_source():
    return {'schemaVersion': 3, 'component': dict(IDENTITY)}


def expected_deployment():
    return {'subsystem': 'textus-cbd-standalone', 'version': '0.1.0-SNAPSHOT',
            'components': [IDENTITY], 'security': {'authentication': AUTHENTICATION}}


class TargetFixtures(unittest.TestCase):
    """Own only fresh temporary inputs and outputs under Cozy target/phase65."""

    def setUp(self):
        TARGET.mkdir(parents=True, exist_ok=True)
        temporary = tempfile.TemporaryDirectory(prefix='s654c-unit-', dir=TARGET)
        self.addCleanup(temporary.cleanup)
        self.root = LIVE.safe_path(temporary.name)
        self.run = self.root / 'run'
        self.run.mkdir()
        self.original = self.root / 'original-subsystem-descriptor.yaml'
        self.original.write_text('subsystem: textus-cbd-standalone\n', encoding='utf-8')

    def car(self, source):
        return self.car_bytes(json.dumps(source).encode('utf-8'))

    def car_bytes(self, raw):
        car = self.root / 'selected.car'
        with zipfile.ZipFile(car, 'w', zipfile.ZIP_DEFLATED) as archive:
            archive.writestr('component-descriptor.json', raw)
        return car

    def admission_failure(self, car):
        with self.assertRaises(ValueError) as observed:
            LIVE.write_deployment_sar(car, self.original, self.run)
        return observed.exception


class CanonicalCarAdmission(TargetFixtures):
    """Only the declared schema and actual canonical CBD identity are admitted."""

    def test_real_identity_emits_canonical_binding_and_existing_local_subject(self):
        # Given a CAR with schema 3 and the actual CBD namespace/id/version.
        car = self.car(canonical_source())
        # When its component descriptor is admitted without launching the runtime.
        deployment = LIVE.cbd_deployment_descriptor(car)
        # Then the canonical binding and existing fixed local-subject policy result.
        self.assertEqual(deployment, expected_deployment())

    def test_unsupported_schema_versions_including_bool_are_rejected(self):
        for version in (None, False, True, 2, 4, 3.0, '3'):
            with self.subTest(schemaVersion=version):
                # Given a canonical identity under an unsupported schema version.
                source = canonical_source()
                source['schemaVersion'] = version
                car = self.car(source)
                # When deployment admission is attempted.
                error = self.admission_failure(car)
                # Then a named schema failure precedes any deployment output.
                self.assertRegex(str(error), r'CBD deployment admission failed: schemaVersion')
                self.assertEqual(list(self.run.iterdir()), [])

    def test_missing_schema_version_is_rejected(self):
        # Given a descriptor with canonical identity but no schemaVersion.
        source = canonical_source()
        del source['schemaVersion']
        car = self.car(source)
        # When deployment admission is attempted.
        error = self.admission_failure(car)
        # Then the missing schema is rejected before deployment output.
        self.assertRegex(str(error), r'CBD deployment admission failed: schemaVersion')
        self.assertEqual(list(self.run.iterdir()), [])

    def test_missing_or_nonobject_component_is_rejected(self):
        for value in ('missing', None, [], 'CbdSupport', 3, True):
            with self.subTest(component=value):
                # Given a descriptor whose component is absent or not an object.
                source = {'schemaVersion': 3}
                if value != 'missing':
                    source['component'] = value
                car = self.car(source)
                # When deployment admission is attempted.
                error = self.admission_failure(car)
                # Then component-object admission fails without packaging anything.
                self.assertRegex(str(error), r'CBD deployment admission failed: component object')
                self.assertEqual(list(self.run.iterdir()), [])

    def test_each_missing_nonstring_or_empty_identity_field_is_rejected(self):
        for field in IDENTITY:
            for value in ('missing', None, False, 3, [], {}, ''):
                with self.subTest(field=field, value=value):
                    # Given a missing, nonstring or empty canonical identity field.
                    source = canonical_source()
                    if value == 'missing':
                        del source['component'][field]
                    else:
                        source['component'][field] = value
                    car = self.car(source)
                    # When deployment admission is attempted.
                    error = self.admission_failure(car)
                    # Then that field is named in the failure before packaging.
                    self.assertIn(f'CBD deployment admission failed: component.{field}', str(error))
                    self.assertEqual(list(self.run.iterdir()), [])

    def test_each_conflicting_canonical_identity_field_is_rejected(self):
        for field in IDENTITY:
            with self.subTest(field=field):
                # Given a string identity that conflicts with the actual CBD CAR.
                source = canonical_source()
                source['component'][field] = 'conflicting-' + IDENTITY[field]
                car = self.car(source)
                # When deployment admission is attempted.
                error = self.admission_failure(car)
                # Then the conflict is rejected without a guessed replacement.
                self.assertIn(f'CBD deployment admission failed: component.{field}', str(error))
                self.assertEqual(list(self.run.iterdir()), [])

    def test_legacy_component_and_coordinate_do_not_supply_guessed_identity(self):
        for identity in ({'component': 'CbdSupport', 'coordinate': 'org.simplemodeling.textus:CbdSupport:0.1.0-SNAPSHOT'},
                         {'componentName': 'CbdSupport'}, {'name': 'CbdSupport'}):
            with self.subTest(identity=identity):
                # Given legacy identity aliases without namespace/id/version.
                car = self.car({'schemaVersion': 3, 'component': identity})
                # When deployment admission is attempted.
                error = self.admission_failure(car)
                # Then aliases never become a guessed canonical binding.
                self.assertIn('CBD deployment admission failed: component.namespace', str(error))
                self.assertEqual(list(self.run.iterdir()), [])

    def test_malformed_json_or_encoding_has_named_admission_failure(self):
        for raw in (b'{', b'\xff'):
            with self.subTest(raw=raw):
                # Given an actual ZIP whose descriptor cannot be decoded as JSON.
                car = self.car_bytes(raw)
                # When deployment admission is attempted.
                error = self.admission_failure(car)
                # Then a named parse failure occurs before deployment output.
                self.assertIn('CBD deployment admission failed: component-descriptor.json', str(error))
                self.assertEqual(list(self.run.iterdir()), [])

    def test_invalid_zip_has_named_admission_failure(self):
        # Given nonempty input that is not a ZIP archive.
        car = self.root / 'invalid.car'
        car.write_bytes(b'not a ZIP archive')
        # When deployment admission is attempted.
        error = self.admission_failure(car)
        # Then a named archive failure occurs before deployment output.
        self.assertIn('CBD deployment admission failed: component-descriptor.json', str(error))
        self.assertEqual(list(self.run.iterdir()), [])

    def test_absent_descriptor_entry_has_named_admission_failure(self):
        # Given a ZIP CAR with no component-descriptor.json entry.
        car = self.root / 'no-descriptor.car'
        with zipfile.ZipFile(car, 'x') as archive:
            archive.writestr('unrelated.txt', 'no descriptor')
        # When deployment admission is attempted.
        error = self.admission_failure(car)
        # Then the absent descriptor is named and deployment remains untouched.
        self.assertIn('CBD deployment admission failed: component-descriptor.json', str(error))
        self.assertEqual(list(self.run.iterdir()), [])

    def test_nonobject_descriptor_is_rejected(self):
        for source in (None, [], 'legacy descriptor'):
            with self.subTest(source=source):
                # Given valid JSON that is not a descriptor object.
                car = self.car(source)
                # When deployment admission is attempted.
                error = self.admission_failure(car)
                # Then descriptor-object admission fails before packaging.
                self.assertIn('CBD deployment admission failed: descriptor object', str(error))
                self.assertEqual(list(self.run.iterdir()), [])


class PrivateSarPackaging(TargetFixtures):
    """The private SAR and derivation receipt expose the selected artifact binding."""

    def test_packaged_json_has_exact_identity_fixed_user_policy_and_derivation(self):
        # Given a canonical CAR and a readable original provenance descriptor.
        car = self.car(canonical_source())
        # When the private JSON deployment descriptor and SAR are written.
        receipt = LIVE.write_deployment_sar(car, self.original, self.run)
        # Then the sole SAR descriptor contains the canonical identity and policy.
        descriptor = self.run / 'subsystem-descriptor.json'
        sar = self.run / 'component.d/textus-cbd-standalone.sar'
        with zipfile.ZipFile(sar) as archive:
            self.assertEqual(archive.namelist(), ['subsystem-descriptor.json'])
            self.assertEqual(json.loads(archive.read('subsystem-descriptor.json')), expected_deployment())
        self.assertEqual(json.loads(descriptor.read_text(encoding='utf-8')), expected_deployment())
        self.assertEqual(receipt, {
            'sourceCar': str(car), 'sourceDescriptor': str(self.original),
            'componentIdentity': IDENTITY, 'generatedDescriptor': str(descriptor),
            'generatedArchive': str(sar), 'userMode': 'standalone',
            'authenticationPolicy': AUTHENTICATION,
        })


class OwnedGroupObservation(unittest.TestCase):
    """Exact native group observations distinguish absence from failed observation."""

    def test_empty_owned_group_is_absent_with_exact_query_and_raw_evidence(self):
        # Given the selected macOS empty-group result for the native owned group.
        group = 9834
        observations = []
        result = LIVE.subprocess.CompletedProcess([], 1, '', '')
        with mock.patch.object(LIVE.subprocess, 'run', return_value=result) as query:
            # When the exact group is observed through the native query boundary.
            present = LIVE.owned_group_exists(group, observations)
        # Then absence follows only the exact empty exit1 result and retains its query.
        self.assertFalse(present)
        argv = ['/bin/ps', '-g', '9834', '-o', 'pid=,pgid=']
        query.assert_called_once_with(argv, capture_output=True, text=True, timeout=2,
                                      env={**LIVE.os.environ, 'LC_ALL': 'C'})
        self.assertEqual(observations[0]['argv'], argv)
        self.assertEqual((observations[0]['exitCode'], observations[0]['stdout'],
                          observations[0]['stderr']), (1, '', ''))
        self.assertIn('startedAt', observations[0])
        self.assertIn('finishedAt', observations[0])

    def test_owned_leader_and_children_or_child_after_leader_exit_are_present(self):
        for rows in ('9834 9834\n9835 9834\n', '9835 9834\n'):
            with self.subTest(rows=rows):
                # Given owned leader/child rows, or a child remaining after leader exit.
                observations = []
                result = LIVE.subprocess.CompletedProcess([], 0, rows, '')
                with mock.patch.object(LIVE.subprocess, 'run', return_value=result) as query:
                    # When that native owned group alone is queried.
                    present = LIVE.owned_group_exists(9834, observations)
                # Then either form proves presence without inspecting any other group.
                self.assertTrue(present)
                self.assertEqual(query.call_args.args[0], ['/bin/ps', '-g', '9834', '-o', 'pid=,pgid='])
                self.assertEqual(query.call_count, 1)
                self.assertEqual(observations[0]['stdout'], rows)

    def test_unusable_native_rows_and_results_fail_instead_of_proving_absence(self):
        cases = ((0, '9834\n', ''), (0, 'x 9834\n', ''), (0, '0 9834\n', ''),
                 (0, '9834 9834 extra\n', ''), (0, '9834 9833\n', ''),
                 (0, '', ''), (1, '9834 9834\n', ''), (1, '', 'denied'),
                 (0, '9834 9834\n', 'warning'), (2, '', ''))
        for exit_code, stdout, stderr in cases:
            with self.subTest(exitCode=exit_code, stdout=stdout, stderr=stderr):
                # Given malformed, mismatched or otherwise unadmitted native output.
                observations = []
                result = LIVE.subprocess.CompletedProcess([], exit_code, stdout, stderr)
                with mock.patch.object(LIVE.subprocess, 'run', return_value=result):
                    # When the owned group observation is attempted.
                    with self.assertRaisesRegex(ValueError, 'Owned process group observation failed:'):
                        LIVE.owned_group_exists(9834, observations)
                # Then a named failure retains actual output and never returns absence.
                self.assertEqual(len(observations), 1)
                self.assertEqual((observations[0]['exitCode'], observations[0]['stdout'],
                                  observations[0]['stderr']), (exit_code, stdout, stderr))

    def test_query_timeout_and_permission_error_retain_named_raw_failure(self):
        failures = (LIVE.subprocess.TimeoutExpired(['/bin/ps'], 2, output='partial', stderr='timeout'),
                    PermissionError(1, 'Operation not permitted'))
        for failure in failures:
            with self.subTest(failure=type(failure).__name__):
                # Given a timeout or permission failure at the native query boundary.
                observations = []
                with mock.patch.object(LIVE.subprocess, 'run', side_effect=failure) as query:
                    # When the selected group cannot be observed.
                    with self.assertRaisesRegex(ValueError, 'Owned process group observation failed:'):
                        LIVE.owned_group_exists(9834, observations)
                # Then raw failure evidence survives without reinterpreting it as absence.
                self.assertEqual(query.call_count, 1)
                self.assertEqual(len(observations), 1)
                self.assertEqual(observations[0]['error'], str(failure))
                self.assertIn('startedAt', observations[0])
                self.assertIn('finishedAt', observations[0])
                if isinstance(failure, LIVE.subprocess.TimeoutExpired):
                    self.assertTrue(observations[0]['timedOut'])
                    self.assertEqual((observations[0]['stdout'], observations[0]['stderr']), ('partial', 'timeout'))


class OwnedCleanup(unittest.TestCase):
    """Cleanup errors survive best-effort owned signals and native child reaping."""

    def test_signal_denial_and_query_failure_keep_cleanup_unsuccessful_and_attempt_wait(self):
        for failure in ('signal-denial', 'query-failure'):
            with self.subTest(failure=failure):
                # Given a native owned child and a denied TERM or failed group query.
                child = mock.Mock(pid=9834)
                child.poll.return_value = -15
                child.wait.return_value = -15
                live = LIVE.LiveRun(mock.Mock(), Path('/unused'), Path('/unused'))
                absent = LIVE.subprocess.CompletedProcess([], 1, '', '')
                queries = [absent, absent] if failure == 'signal-denial' else [
                    PermissionError(1, 'Operation not permitted'), absent]
                signals = PermissionError(1, 'Operation not permitted') if failure == 'signal-denial' else None
                with mock.patch.object(LIVE.os, 'killpg', side_effect=signals) as signal_group, \
                        mock.patch.object(LIVE.subprocess, 'run', side_effect=queries) as query, \
                        mock.patch.object(LIVE.time, 'sleep'):
                    # When cleanup makes bounded best-effort attempts on the owned group.
                    receipt = live.stop_group(child, child.pid)
                # Then errors remain fatal even after wait and a later observed absence.
                self.assertFalse(receipt['success'])
                self.assertTrue(receipt['childExited'])
                self.assertTrue(receipt['groupGone'])
                child.wait.assert_called_once_with(timeout=10)
                self.assertEqual(receipt['errors'][0]['stage'],
                                 'term-signal' if failure == 'signal-denial' else 'term-observation')
                self.assertIn('Operation not permitted', receipt['errors'][0]['error'])
                expected_signals = [mock.call(9834, LIVE.signal.SIGTERM)]
                if failure == 'query-failure':
                    expected_signals.append(mock.call(9834, LIVE.signal.SIGKILL))
                self.assertEqual(signal_group.call_args_list, expected_signals)
                self.assertEqual(query.call_count, 2)
                self.assertEqual(len(receipt['groupObservations']), 2)
                self.assertTrue(all(call.args[0] == ['/bin/ps', '-g', '9834', '-o', 'pid=,pgid=']
                                    for call in query.call_args_list))


class CurrentConsumerPreparation(TargetFixtures):
    """Only current authored consumer inputs refresh the two selected scratch files."""

    def test_current_files_replace_stale_outputs_preserve_unrelated_files_and_reject_symlinks(self):
        cases = ('refresh', 'source-pubspec', 'source-contract', 'destination-pubspec',
                 'destination-contract', 'source-ancestor', 'destination-ancestor')
        for case in cases:
            with self.subTest(case=case):
                # Given authored fixtures, stale outputs and unrelated retained scratch.
                cozy = self.root / case
                authored = cozy / 'examples/dart-cbd-consumer/consumer'
                workspace = cozy / 'target/phase65/s654'
                consumer = workspace / 'consumer'
                (authored / 'bin').mkdir(parents=True)
                (consumer / 'bin').mkdir(parents=True)
                names = ('pubspec.yaml', 'bin/contract.dart')
                for name in names:
                    (authored / name).write_text('authored ' + name + '\n', encoding='utf-8')
                    (consumer / name).write_text('stale ' + name + '\n', encoding='utf-8')
                retained = workspace / 'preparation.json'
                retained.write_text('original provenance\n', encoding='utf-8')
                unrelated = consumer / '.dart_tool'
                unrelated.mkdir()
                (unrelated / 'retained.txt').write_text('retain\n', encoding='utf-8')
                if case != 'refresh':
                    source = case.startswith('source-')
                    selected = authored if source else consumer
                    if case.endswith('ancestor'):
                        original = selected / 'bin'
                        actual = selected / 'actual-bin'
                    else:
                        original = selected / ('pubspec.yaml' if case.endswith('pubspec') else 'bin/contract.dart')
                        actual = original.with_name('actual-' + original.name)
                    original.rename(actual)
                    original.symlink_to(actual, target_is_directory=case.endswith('ancestor'))
                # When preparation of the exact two current files is attempted.
                if case == 'refresh':
                    receipt = LIVE.prepare_consumer(cozy, workspace)
                    # Then stale content is replaced and actual two-file provenance is returned.
                    self.assertEqual(receipt, {'files': [
                        {'source': str(authored / name), 'destination': str(consumer / name)} for name in names]})
                    for name in names:
                        self.assertEqual((consumer / name).read_text(encoding='utf-8'), 'authored ' + name + '\n')
                else:
                    with self.assertRaisesRegex(ValueError, 'Symlink path is not admitted:'):
                        LIVE.prepare_consumer(cozy, workspace)
                    # Then symlink input/output or ancestor is rejected before refreshing files.
                    self.assertEqual((consumer / 'pubspec.yaml').read_text(encoding='utf-8'), 'stale pubspec.yaml\n')
                    self.assertEqual((consumer / 'bin/contract.dart').read_text(encoding='utf-8'), 'stale bin/contract.dart\n')
                # Then unrelated output and original preparation provenance stay intact.
                self.assertEqual(retained.read_text(encoding='utf-8'), 'original provenance\n')
                self.assertEqual((unrelated / 'retained.txt').read_text(encoding='utf-8'), 'retain\n')


class PrivateFixtureCatalog(TargetFixtures):
    """Catalog copying preserves source inputs and admits only fresh private output."""

    def fixture_source(self, source):
        fixtures = source / 'examples/cbd-sie-sar/fixtures'
        inputs = {
            'catalog/metadata/repository/car/index.json': b'{"entries":[{"id":"textus-runtime"}]}\n',
            'catalog/repository/catalog/car/textus-runtime.model-metadata.json': b'{"component":"textus-runtime"}\n',
        }
        for name, raw in inputs.items():
            path = fixtures / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(raw)
        return fixtures, inputs

    def test_absent_sar_generates_empty_index_only_in_private_copy_with_exact_provenance(self):
        # Given fresh source fixtures containing the original CAR and model metadata only.
        source = self.root / 'source'
        fixtures, inputs = self.fixture_source(source)
        sar_relative = 'catalog/metadata/repository/sar/index.json'
        # When catalog preparation copies those inputs into the fresh owned run.
        receipt = LIVE.prepare_fixture_catalog(source, self.run)
        # Then original input bytes survive in both trees and only private SAR is generated.
        served = self.run / 'fixture-input'
        for name, raw in inputs.items():
            self.assertEqual((fixtures / name).read_bytes(), raw)
            self.assertEqual((served / name).read_bytes(), raw)
        self.assertFalse((fixtures / sar_relative).exists())
        self.assertEqual(LIVE.read_json(served / sar_relative), {'entries': [], 'diagnostics': []})
        self.assertEqual(receipt, {'sourceFixtures': str(fixtures), 'servedFixtures': str(served),
                                  'sarIndex': str(served / sar_relative), 'sarIndexGenerated': True})

    def test_existing_sar_profile_bytes_and_entries_are_retained_without_empty_substitution(self):
        # Given a nonempty existing SAR index containing a profile and deliberate formatting.
        source = self.root / 'source'
        fixtures, inputs = self.fixture_source(source)
        sar_relative = 'catalog/metadata/repository/sar/index.json'
        sar = fixtures / sar_relative
        sar.parent.mkdir(parents=True)
        raw = b'{ "entries": [{"id": "existing-profile"}], "diagnostics": [] }\n'
        sar.write_bytes(raw)
        # When catalog preparation copies the existing SAR input.
        receipt = LIVE.prepare_fixture_catalog(source, self.run)
        # Then bytes and entries remain in source and copy with no generated substitution.
        served = self.run / 'fixture-input'
        self.assertEqual(sar.read_bytes(), raw)
        self.assertEqual((served / sar_relative).read_bytes(), raw)
        self.assertEqual(LIVE.read_json(served / sar_relative)['entries'], [{'id': 'existing-profile'}])
        for name, original in inputs.items():
            self.assertEqual((fixtures / name).read_bytes(), original)
            self.assertEqual((served / name).read_bytes(), original)
        self.assertEqual(receipt, {'sourceFixtures': str(fixtures), 'servedFixtures': str(served),
                                  'sarIndex': str(served / sar_relative), 'sarIndexGenerated': False})

    def test_symlink_existing_destination_and_missing_required_inputs_fail_before_binding(self):
        cases = ('source-file', 'source-directory', 'source-ancestor', 'fixture-ancestor',
                 'destination-symlink', 'existing-destination', 'missing-car', 'missing-metadata')
        for case in cases:
            with self.subTest(case=case):
                # Given fresh fixtures with one prohibited link, destination or missing input.
                source = self.root / case / 'source'
                run = self.root / case / 'run'
                fixtures, inputs = self.fixture_source(source)
                run.mkdir()
                served = run / 'fixture-input'
                if case in ('source-file', 'source-directory', 'fixture-ancestor'):
                    selected = fixtures / ('catalog/metadata/repository/car/index.json'
                                           if case == 'source-file' else
                                           'catalog/metadata' if case == 'fixture-ancestor' else 'catalog')
                    actual = selected.with_name('actual-' + selected.name)
                    selected.rename(actual)
                    selected.symlink_to(actual, target_is_directory=case != 'source-file')
                elif case == 'source-ancestor':
                    actual = source.with_name('actual-source')
                    source.rename(actual)
                    source.symlink_to(actual, target_is_directory=True)
                elif case == 'destination-symlink':
                    actual = run / 'actual-fixtures'
                    actual.mkdir()
                    served.symlink_to(actual, target_is_directory=True)
                elif case == 'existing-destination':
                    served.mkdir()
                else:
                    (fixtures / list(inputs)[0 if case == 'missing-car' else 1]).unlink()
                expected = ('Symlink' if 'source-' in case or 'ancestor' in case or 'symlink' in case
                            else 'Fixture catalog destination already exists:' if case == 'existing-destination'
                            else 'Nonempty regular file required:')
                with mock.patch.object(LIVE, 'FixtureServer') as binding:
                    # When private catalog admission is attempted without acquiring any server.
                    with self.assertRaisesRegex(ValueError, expected):
                        LIVE.prepare_fixture_catalog(source, run)
                # Then the named admission failure precedes binding and creates no copied input.
                binding.assert_not_called()
                self.assertFalse((served / 'catalog').exists())
                if case not in ('destination-symlink', 'existing-destination'):
                    self.assertFalse(served.exists())


class FixtureReadiness(TargetFixtures):
    """Both catalog endpoints must succeed through the existing HTTP boundary."""

    def test_car_then_sar_readiness_succeeds_or_propagates_sar_failure(self):
        for failure in (False, True):
            with self.subTest(sarFailure=failure):
                # Given a mock HTTP boundary with CAR success and SAR success or failure.
                live = LIVE.LiveRun(mock.Mock(), self.root, self.run)
                base = 'http://127.0.0.1:1'
                error = ValueError('HTTP failure 404')
                responses = [{'entries': []}, error if failure else {'entries': [], 'diagnostics': []}]
                with mock.patch.object(live, 'http', side_effect=responses) as http:
                    # When fixture readiness checks both selected endpoints sequentially.
                    if failure:
                        with self.assertRaises(ValueError) as observed:
                            live.fixture_ready(base)
                    else:
                        live.fixture_ready(base)
                # Then CAR precedes SAR exactly and SAR failure remains the same failure.
                self.assertEqual(http.call_args_list, [
                    mock.call('fixture-car-readiness', base + '/catalog/metadata/repository/car/index.json'),
                    mock.call('fixture-sar-readiness', base + '/catalog/metadata/repository/sar/index.json')])
                if failure:
                    self.assertIs(observed.exception, error)


class GeneratedRequestScenarios(TargetFixtures):
    """Mock-only payloads specify exact request admission, never real CBD proof."""

    def test_provided_and_absent_requests_preserve_exact_envelope_and_typed_roundtrip(self):
        for detail in ('unit-marker', None):
            with self.subTest(detail=detail):
                # Given a mock owned child and complete controlled payload, without a launch.
                request = {} if detail is None else {'detail': detail}
                payload = {'overall': 'degraded', 'source_count': 5, 'ready_source_count': 3,
                           'component_count': 1,
                           'detail': detail if detail is not None else 'fixture-catalog=ready'}
                report = {'encodedRequest': request, 'decodedResponse': payload,
                          'overall': payload['overall'], 'detail': payload['detail'],
                          'sourceCount': 5, 'readySourceCount': 3, 'componentCount': 1}
                live = LIVE.LiveRun(mock.Mock(command_timeout_seconds=180), self.root, self.run)
                live.java = mock.Mock(pid=9834)
                label = 'explicit-detail' if detail is not None else 'omitted-detail'
                base, dart, consumer = 'http://127.0.0.1:1', Path('/mock/dart'), self.root / 'consumer'
                response = {'result': {'content': [{'type': 'text', 'text': json.dumps(payload)}]}}
                with mock.patch.object(live, 'command', side_effect=[json.dumps(request), json.dumps(report)]) as command, \
                        mock.patch.object(live, 'http', return_value=response) as http:
                    # When the scenario uses mock command/HTTP boundaries for the exact argument form.
                    live.scenario(label, detail, base, dart, consumer)
                # Then unchanged generated maps enter the envelope and complete typed observations pass.
                http.assert_called_once_with(label, base + '/mcp',
                    {'jsonrpc': '2.0', 'id': label, 'method': 'tools/call',
                     'params': {'name': LIVE.TOOL, 'arguments': request}}, timeout=180)
                detail_args = [] if detail is None else [detail]
                self.assertEqual(command.call_args_list, [
                    mock.call(label + '-request', [str(dart), 'run', 'bin/contract.dart', 'request', *detail_args], consumer),
                    mock.call(label + '-response', [str(dart), 'run', 'bin/contract.dart', 'response',
                              str(self.run / (label + '-live-payload.json')), *detail_args], consumer)])
                evidence = LIVE.read_json(self.run / (label + '-observation.json'))
                self.assertEqual(evidence['when']['generatedRequest'], request)
                self.assertEqual(evidence['then']['typedReport'], report)
                self.assertTrue(evidence['then']['requestMatchesSent'])
                self.assertTrue(evidence['then']['responseMatchesLive'])
                self.assertTrue(evidence['then']['fixtureFedReadiness'])
                self.assertEqual(evidence['given']['generatedSdk'], str(self.root / 'sdk-cncf-snake-omit'))
                self.assertEqual(evidence['given']['requestEncoding'], 'toJsonOmittingNullFields')
                self.assertEqual(evidence['given']['jsonFieldStyle'], 'cncf-snake-case')
                self.assertEqual(evidence['then']['detailObservation'],
                                 'echo' if detail is not None else 'runtime source summary')

    def test_present_null_omitted_request_is_rejected_before_http(self):
        # Given a mock generated present-null request for the genuinely absent scenario.
        live = LIVE.LiveRun(mock.Mock(command_timeout_seconds=180), self.root, self.run)
        live.java = mock.Mock(pid=9834)
        with mock.patch.object(live, 'command', return_value=json.dumps({'detail': None})) as command, \
                mock.patch.object(live, 'http') as http:
            # When request-shape admission compares it with the exact empty map.
            with self.assertRaisesRegex(ValueError, 'Generated request-shape admission failed:'):
                live.scenario('omitted-detail', None, 'http://127.0.0.1:1', Path('/mock/dart'), self.root / 'consumer')
        # Then rejection retains generated evidence and happens before any mock HTTP call.
        command.assert_called_once()
        http.assert_not_called()
        self.assertEqual(live.result['scenarios'][0]['when']['generatedRequest'], {'detail': None})
        self.assertEqual(live.result['scenarios'][0]['then'], {})


def main():
    TARGET.mkdir(parents=True, exist_ok=True)
    suite = unittest.defaultTestLoader.loadTestsFromModule(sys.modules[__name__])
    result = unittest.TextTestRunner(verbosity=2).run(suite)
    success = result.testsRun > 0 and result.wasSuccessful()
    LIVE.write_json(TARGET / 's654g-unit-result.json', {
        'testsRun': result.testsRun, 'failures': len(result.failures),
        'errors': len(result.errors), 'success': success,
    })
    return 0 if success else 1


if __name__ == '__main__':
    raise SystemExit(main())
