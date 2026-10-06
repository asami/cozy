# Real CBD generated Dart consumer

This independent SDK-only package exercises the generated
`CbdSupportCbdRetrievalApi.status(CbdStatusRequest)` contract. Its only dependency
is the adjacent generated `phase65_cbd_sdk` path package at `../sdk-cncf-snake-omit`,
explicitly generated with `--json-field-style cncf-snake-case`. The default
`source` profile retains original field spelling for existing callers; this
consumer selects the CNCF runtime profile while preserving original Dart API
names, canonical identities, scalar wrappers and discriminator values. Request
output and the injected function select generated
`Map<String, Object?> toJsonOmittingNullFields()` directly. Its typed return
supersedes the earlier redundant object-type guard. The implementation decodes its result with
`CbdStatusResponse.fromJson`. No request, response or domain model is handwritten.
The Python harness owns HTTP/MCP mechanics outside the generator and Dart API.

Current S65.4G status: actual unit24 and [live007 product proof](../../target/phase65/s654/runs/live-gru8e1bo/result.json)
passed both original scenarios, CAR/SAR readiness HTTP200, both real MCP HTTP200,
full typed responses, genuine {} omission, marker echo, fixture-catalog ready
and strict cleanup. Overall remains degraded because simplemodeling is degraded.
S65.4F actual251/14 suites/31 SDK commands/six real-CML products/unit20 are reused;
live006's CAR200/SAR404 failure remains historical.
S65.4 is independently accepted and was manually committed at native
`0237579262fd8b86c7b904ecca6cb49f18fd78e1`.
P65-S654-REVIEW-001 r1 from `/root/p65_s654_review_001` returned PASS with zero
Current Boundary Blockers and one separate nonblocking Hygiene item,
HYG-P65-S654-MODELER-LOCAL-NAMING-001, recorded in the
[Hygiene follow-up](../../docs/journal/2026/10/2026-10-05-phase-65-hygiene-follow-up.md).
At S65.4 Step acceptance on 2026-10-06, the historical snapshot recorded eight review entries and FULL0. After the sole full Phase review, P65-REVIEW-LEDGER-001 revision 11 records ten entries and FULL1, before the parent's following release M0 amendment. DART-08 and DART-09
are completed in accepted S65.4; no external UI integration is claimed.
Native acceptance history records local Step commits.
All eight original checklist boxes are checked; all four original groups are CLOSED.
Sole full Phase review P65-FULL-REVIEW-001 r1 PASS and repository-full tests PASS provide the final release closure basis P65-RELEASE-CLOSURE-001 r1.

The source is the actual tracked working tree of
`textus-cbd-support:src/main/cozy/textus-cbd-support.cml`, with the real
`ComponentFactory.StatusActionCallImpl` producer, standalone SAR descriptor and
catalog fixtures. The preparation receipt records its observed native HEAD,
complete porcelain dirt and copied paths. This is executed-input provenance,
not an immutability certificate. Catalog fixtures feed the real CBD runtime;
they never supply canned status responses. The source checkout is read-only.

The procedure below belongs to PHASE-65/S65.4. S65.3 generation is accepted at native
`770bf6b380a292c027fa059c7f50cd58dfcbd00c`. Keep Cozy `0.3.3-SNAPSHOT` and CNCF
`0.5.3-SNAPSHOT` unchanged; this procedure does not publish anything.

Private CAR generation and the export of 111 dependency JARs passed. The initial
real-source SDK generation failed on the absent canonical legacy QUERY request;
an earlier `newSpec` compilation also failed and remains historical evidence.
After the S65.4B original-Section bridge, current focused validation passed all
13 suites, 217/217 tests, with sixteen SDK command reports, zero SBT/wrapper
exits and `lock=released`. Fresh SDK generation from the unchanged real CBD CML
passed with all six products. The first live attempt failed before readiness,
ran zero scenarios and completed owned cleanup. No live consumer proof is claimed
at that first attempt.
S65.4C's CAR/SAR behavioral regression passed 12/12 with zero failures/errors
in `target/phase65/s654c-unit-result.json`. Historical live001 remains the
pre-readiness deployment failure above. Live002 (`s654/runs/live-2bqktlyp`)
reached the owned successful bind, HTTP 200 OpenAPI and successful `tools/list`
with exactly one `org.simplemodeling.textus.CbdSupport.CbdRetrieval.status`.
VAL-P65-S654C-005 failed before scenarios because the private locator omitted
that namespace; zero scenarios ran and owned Java/fixture cleanup succeeded.
The corrected private MCP `params.name` uses the fully qualified identity,
while generator `--operations CbdSupport.CbdRetrieval.status` and API types
remain unchanged. Live003 (`s654/runs/live-1ymsxpks`) reached owned readiness,
SDK offline pub/analysis and consumer pub, then failed consumer analysis because
generated `CbdStatusRequest.toJson()` returns `Object?` at the typed transport
boundary. Zero live scenarios ran. Cleanup recorded TERM followed by
`Operation not permitted` and `success=false`; the failing syscall is unknown.
Later scoped probes observed PID/group 9834 absent. That separate observation
does not repair live003's cleanup receipt or establish its cause. Failed runs
and the original raw-submission limits remain retained. S65.4D's consumer type
check, refresh, strict cleanup observation and six regressions subsequently
passed unit18 and live004 prerequisites as described below. At that historical
attempt, independent S65.4 review/commit, repository-full tests, full Phase review
and release remained pending, as did both final original checklist items.

## Prerequisites and locations

Use managed Python >=3.12 through `uv`, the existing exact PATH Cozy executable,
Java and normal SBT dependency caches with the declared CNCF artifact available.
Use the direct Dart executable, currently
`/opt/homebrew/share/flutter/bin/cache/dart-sdk/bin/dart`; no Flutter upgrade or
third-party Dart package is required. Configure repository, launcher, Dart and
Java locations explicitly for the current machine. The source snapshot and
workspace must be fresh and nonexistent for initial preparation; S65.4F reuses
the retained preparation and creates a fresh `sdk-cncf-snake-omit` output. Previous evidence
is retained, never replaced or removed by these scripts.

All runtime products are under `<CozyRoot>/target/phase65/`. The source snapshot
is `cbd-runtime-source`; the acceptance workspace is `s654`, containing original
`sdk`, retained explicit-profile `sdk-cncf-snake`, fresh `sdk-cncf-snake-omit`,
`consumer`, `preparation.json`,
exported classpath and retained unique `runs`.
Only the copied build receives separate top-level scoped SBT settings for the
exact Cozy launcher/current compiled runtime and dependency-only CNCF classpath.
Original sources, plugins, dependencies and runtime compatibility remain intact.

## Native preparation, generation and acceptance

For CNCF workflows, execute preparation through the registered one-shot runtime
runner, SBT through the registered serialized command runner, generation through
the Cozy command runner, and acceptance through the runtime-session runner after
parent script/command admission. Wait for each SBT `lock=released` marker.
The following shell variables are illustrative explicit location bindings:

```sh
COZY_ROOT=/Users/asami/src/dev2026/cozy
CBD_SOURCE=/Users/asami/src/dev2026/textus-cbd-support
COZY_EXECUTABLE='/Users/asami/Library/Application Support/Coursier/bin/cozy'
DART_EXECUTABLE=/opt/homebrew/share/flutter/bin/cache/dart-sdk/bin/dart
JAVA_EXECUTABLE=/usr/bin/java
WORKSPACE="$COZY_ROOT/target/phase65/s654"
CBD_COPY="$COZY_ROOT/target/phase65/cbd-runtime-source"
```

1. Prepare only fresh private files, without build or launch:

   ```sh
   uv run --no-cache --managed-python --no-python-downloads --script \
     "$COZY_ROOT/scripts/test/phase65/prepare-cbd-consumer.py" \
     --cozy-root "$COZY_ROOT" --cbd-source "$CBD_SOURCE" \
     --workspace "$WORKSPACE" --cozy-executable "$COZY_EXECUTABLE"
   ```

2. In the Cozy root, have the serialized runner execute
   `sbt --batch "cozyExportRuntimeClasspath"`. Then, in `$CBD_COPY`, have it
   execute `sbt --batch "cozyBuildCAR" "phase65WriteCncfClasspath"` using normal
   caches. The delegated generator uses the existing Cozy launcher with
   `--runtime 0.3.3-SNAPSHOT --runtime-dev-dir "$COZY_ROOT"`; no nested SBT or
   shared warehouse publication is used.

3. Generate the complete six-product profile SDK into the fresh destination
   through the exact Cozy executable; retain original `sdk` and `sdk-cncf-snake`:

   ```sh
   "$COZY_EXECUTABLE" --runtime 0.3.3-SNAPSHOT --runtime-dev-dir "$COZY_ROOT" \
     modeler dart "$CBD_COPY/src/main/cozy/textus-cbd-support.cml" \
     --package phase65_cbd_sdk --operations CbdSupport.CbdRetrieval.status \
     --json-field-style cncf-snake-case \
     --save "$WORKSPACE/sdk-cncf-snake-omit" --charset UTF-8
   ```

4. Run one owned acceptance lifecycle:

   ```sh
   uv run --no-cache --managed-python --no-python-downloads --script \
     "$COZY_ROOT/scripts/test/phase65/run-cbd-consumer.py" \
     --cozy-root "$COZY_ROOT" --workspace "$WORKSPACE" \
     --cbd-source-copy "$CBD_COPY" --dart-sdk "$DART_EXECUTABLE" \
     --java "$JAVA_EXECUTABLE" --startup-timeout-seconds 120 \
     --command-timeout-seconds 180
   ```

Before any Java/fixture acquisition, the harness reads schemaVersion integer 3
and the actual `namespace=org.simplemodeling.textus`, `id=CbdSupport`,
`version=0.1.0-SNAPSHOT` from `component-descriptor.json` inside the real CAR.
It rejects malformed, missing, legacy-only or conflicting identity with a named
admission failure. It derives a Cozy-owned private JSON SAR containing only
`subsystem-descriptor.json`; the original copied YAML descriptor and failed
deployment evidence remain read-only provenance. The real CAR is copied unchanged.
The descriptor retains `convention=disabled`, `fallback_privilege=disabled` and
the fixed local subject `cbd-standalone-review`, role `reviewer`, security level
`user`, installation `cbd-standalone`. Explicit
`textus.subsystem.user-mode=standalone` selects that existing policy alongside
`textus.subsystem=textus-cbd-standalone`. This uses no runtime test descriptor,
`textus.test=true`, authentication-bypass flag or administrator escalation.
The run and `server-process.json` record artifact derivation and effective mode.

After canonical CAR/SAR admission and before Java version/fixture acquisition,
the harness refreshes only current authored `consumer/pubspec.yaml` and
`consumer/bin/contract.dart` into the exact workspace's `consumer` directory.
Inputs, outputs and ancestors reject symlinks; existing outputs must be regular
files. It creates only consumer/bin directories and preserves original `sdk`,
`.dart_tool`, source snapshot, old runs and original `preparation.json`.
`consumerPreparation` records actual source/destination locators in the live
result and Given observation; original preparation remains source provenance
and does not describe the refreshed consumer files.

The [behavioral regression](../../scripts/test/phase65/test_run_cbd_consumer.py)
tests CAR admission and decoded packaged JSON without launching a runtime. Its
owned temporary fixtures and `s654c-unit-result.json` stay under `target/phase65`;
this historical separate regression receipt passed 12/12 through the registered
one-shot runner and is not DART-08 live proof. S65.4D retains those twelve methods
and authors six additional behavioral methods for exact empty/present groups,
malformed query results, timeout/permission failure, failed owned cleanup and
current two-file consumer refresh with symlink rejection. Native boundaries are
mocked, with adjacent Given/When/Then in each method and parameterized subtests.
S65.4D wrote actual counts/status to fresh `s654d-unit-result.json`,
failed on no tests, and preserved the old receipt. Actual S65.4D execution passed
18/18 with zero failures/errors in `target/phase65/s654d-unit-result.json`; this
is separate unit evidence for unchanged CAR/SAR/preparation/group/lifecycle behavior.

The harness continues to check the actual Java runtime version, starts its own loopback
fixture/server with isolated product home/resource trees, requires the owned
successful bind plus actual `/openapi.json` and `tools/list`, runs offline pub
resolution and strict analysis for both packages, then sends two live generated
requests. The explicit-detail scenario uses a unique marker; the omitted-detail
scenario sends exactly {} through generated `toJsonOmittingNullFields()`.
Before HTTP the harness requires exactly {"detail": marker} for the provided
marker or {} for the absent argument, rejecting present-null and additional
keys without filtering generated output. It rejects HTTP, JSON-RPC/tool,
ambiguous-content and codec failures. Each actual text payload is consumed by
the independent Dart package through the generated interface. Observations
record the exact `sdk-cncf-snake-omit` location, `cncf-snake-case` profile and
`requestEncoding=toJsonOmittingNullFields` in Given
provenance and compare the captured request with sent arguments, the full typed
re-encoding with live JSON structurally, named wrapper values and each original
Dart report count against its fixed actual wire field (`sourceCount` to
`source_count`, `readySourceCount` to `ready_source_count`, `componentCount` to
`component_count`),
fixture readiness, the marker echo and actual omitted-detail source summary.

`consumer/bin/contract.dart request [detail]` prints only generated request JSON.
`response <live-payload-file> [detail]` consumes the current harness-extracted
live payload and prints one typed observation report. Response mode is not a
stored-response success substitute.

The terminal JSON prints the exact current run and result locator; the
`run-result.json` pointer records that same invocation. Select that locator for
review, without guessing the newest directory. Receipts retain argv, cwd,
stdout/stderr, exits/timeouts, raw HTTP responses, sent envelopes, source
provenance, process ownership, scenario Given/When/Then observations and
cleanup. Success requires completed commands, both live proofs and successful
owned Java-group/fixture shutdown. Signals and failure retain evidence and
enter the same cleanup path. Owned cleanup uses the selected macOS query
`/bin/ps -g <owned-group> -o pid=,pgid=` with no shell, a two-second timeout,
preserved PATH and `LC_ALL=C`. Each actual query retains argv, start/finish,
exit/stdout/stderr or timeout/OSError. Only exit1 with empty stdout/stderr proves
absence; exit0 requires nonempty two-positive-integer rows all in the owned
group. Malformed results, mismatched groups, stderr, other exits and query
errors are failures. This contract does not assert universal Linux semantics.
TERM is always attempted on the native owned group, followed by bounded 15s
grace, KILL when absence was not observed, native wait10s and final absence5s.
ESRCH records a signal race rather than group absence. Named stage/errors are
retained even if a later observation succeeds; success requires child exit
from wait, observed group absence, no errors and fixture shutdown/close/join.
Query output never supplies signal targets. A failed cleanup remains failed.

## Historical S65.4E evidence

Actual live004 [result](../../target/phase65/s654/runs/live-nm1w2a4r/result.json)
passed generated SDK and independent consumer offline pub/analysis, generated
explicit-detail request, first actual MCP HTTP200 and strict Java/fixture cleanup.
Its unchanged payload had source_count=5, ready_source_count=3, component_count=1
and scalar overall/detail. Default generated decoding required sourceCount and
failed. Zero complete scenarios passed; the omitted-detail second call did not
run. Original SDK, runs, preparation and raw-retention limits remain intact.

The selected CNCF 0.5.3-SNAPSHOT formatter uses context SnakeCase property naming;
the frozen source-derived profile mirrors its separator normalization and
per-uppercase partitioning (`URLValue` to `u_r_l_value`). The CML and Record
producer keep original camel names. The new generated codec consumes actual
snake keys; there is no runtime adapter, handwritten DTO/codec, payload mutation
or rewritten SDK. Complete `decodedResponse == actual payload` remains mandatory.
The [DA-22 specification](../../docs/spec/dart-model-api-generation.md) and
[profile executable scenarios](../../src/test/scala/cozy/modeler/DartJsonFieldStyleSpec.scala)
passed the actual S65.4E accumulator: 249/249 tests in 14 suites, 21 SDK command
reports, zero SBT/wrapper exits and `lock=released`. Fresh actual real-source
profile generation passed with all six products. Historical 217/217 remains
S65.4B evidence; these results precede the new omission method.

## S65.4F optional absence and actual evidence

Actual live005 [result](../../target/phase65/s654/runs/live-96ligvfe/result.json)
retains both real MCP HTTP200 calls, complete generated typed decode/default
re-encode equality with both full actual payloads, explicit marker echo and
strict owned Java/fixture cleanup PASS. Its second request was {"detail":null}
and returned detail string "null". The required omitted-detail fixture summary
failed (VAL-P65-S654E-009), so this remains partial proof. Earlier preparation,
S65.4B/S65.4E compilation and live001..004 failures remain retained. Original
raw-submission gaps, SDK CLI platform-input absence and corrected native
attribution limitations remain; missing historical input is not reconstructed.

Default zero-argument `Object? toJson()` still emits optional null recursively,
before and after explicit omission calls. The additional data-object
`Map<String, Object?> toJsonOmittingNullFields()` omits only null optional fields
using generated collection-if entries and recurses through required/optional/
list non-scalar data references. Required keys, nonnull values and empty lists
remain. Empty data objects return a typed empty map. Scalar wrappers and
enum/sealed/open classifications receive no omission method and retain original
scalar/discriminator encoding. Constructor/decode checks, identities, both field
profiles and metadata remain unchanged. Omitted decoding retains canonical null
state; omission preserves absence and default encoding restores explicit nulls.
No runtime map filter, handwritten codec or CLI option is introduced.

DA-23/S65.4F authors both-profile collision admission and separate independent
Source/CNCF consumers with complete expected JSON, recursive omission/default
compatibility and decode observations. Two new mock-only Python methods specify
provided/absent envelope/report equality and pre-HTTP present-null rejection.
Controlled unit payloads are not actual CBD evidence. The S65.4F unit receipt is
fresh `target/phase65/s654f-unit-result.json`, preserving historical unit18.
Actual S65.4F validation passed 251/251 tests in 14 suites, with 31 SDK commands
across six independent consumers and one controlled negative, six actual SDK
products generated from unchanged real CBD CML, and unit20 PASS. Historical
249/21/unit18/live005 evidence and all earlier failures remain retained.

Actual live006 (`target/phase65/s654/runs/live-5epmn_uo/result.json`) passed both
real MCP HTTP200 calls, complete typed raw-response equality, genuine {} omission,
marker echo and strict cleanup. The omitted response reported
`fixture-catalog=degraded`: the actual CAR index was HTTP200 and SAR index HTTP404.
Live acceptance remained false at that attempt (VAL-P65-S654F-010).

## S65.4G private catalog input and actual validation

The harness now copies the source's `examples/cbd-sie-sar/fixtures` into each
fresh run's `fixture-input` directory before fixture acquisition. It admits
safe paths, rejects recursive/ancestor symlinks and an existing destination,
and requires nonempty regular CAR index/model metadata inputs. Original fixture
bytes stay unchanged. An absent SAR index is generated only in the private copy
as {"entries":[],"diagnostics":[]}; this means zero SAR profiles and supplies no
schema/schemaVersion, component metadata or canned status response. An existing
nonempty regular SAR index retains its bytes and entries.

`fixturePreparation` records absolute `sourceFixtures`, `servedFixtures`,
`sarIndex` and boolean `sarIndexGenerated` in result, server-process and Given
evidence, separately from source preparation. The server serves `servedFixtures`.
Before socket selection or Java acquisition, sequential `fixture-car-readiness`
and `fixture-sar-readiness` calls must both succeed through the existing bounded
JSON HTTP boundary. Actual receipts/raw responses remain evidence; SAR failure
propagates. Receipt numbering follows recording order, so inspect labels/locators.

Four new Given/When/Then behavior methods cover missing SAR/private generation,
existing SAR retention, named admission failures and mock-only CAR/SAR readiness.
All twenty previous methods remain. Actual [unit24](../../target/phase65/s654g-unit-result.json)
passed 24 tests with zero failures/errors, success true and runner exit0.
Actual [live007](../../target/phase65/s654/runs/live-gru8e1bo/result.json) completed
both original scenarios with owned session007 exit0 and no timeout, signal or
forced termination. CAR/SAR readiness and both real MCP calls returned HTTP200;
the private SAR has empty entries/diagnostics, with source fixtures read-only
and provenance intact. Exact generated marker and genuinely omitted {} requests
passed complete actual typed-response equality and marker echo. Actual counts
are source_count=5, ready_source_count=4, component_count=1; the exact summary is
simplemodeling=degraded, fixture-catalog=ready, empty=ready, local-car=ready,
cache-car=ready, so overall remains degraded. Java28739/group28739 received TERM,
no KILL/errors, wait143 and two native group-absence observations; fixture close/
thread join and strict cleanup passed. Complete submitted/native initial/terminal
records remain retained after one evidence-only continuation, with no rerender/
replay; mistaken runner progress literals were superseded. Historical raw
retention limitations remain. Relevant 251/14/31 and six-product SDK evidence is
reused. Product live proof PASS supplies the real consumer evidence for independently
accepted S65.4. All eight original checklist boxes are checked; all four original groups are CLOSED.
Sole full Phase review P65-FULL-REVIEW-001 r1 PASS and repository-full tests PASS provide the final release closure basis P65-RELEASE-CLOSURE-001 r1.

## Later CBD UI reuse

Later CBD UI code imports the generated package barrel and reuses its request,
response, open scalar wrappers, typed operation interface and codecs. UI,
transport, authentication, state and storage policy stay in the consumer.
Do not create a parallel client-local domain model or infer enums from observed
status strings. This accepted example proves a contract boundary; it does
not claim that a CBD UI integration has occurred.

## Final acceptance — 2026-10-06

Phase 65 is CLOSED: all nine original DART-01..09 outcomes and all eight
original checkbox texts in four original groups are satisfied, as recorded in
[Phase 65](../../docs/phase/phase-65.md) and its [checklist](../../docs/phase/phase-65-checklist.md).
The four actual native Step commits are S65.1
`f4a6e3b0f41fd929fba085664ae958330b82cff0`, S65.2
`716d208ce8505746ac03a3cc3606299f5c4e10e4`, S65.3
`770bf6b380a292c027fa059c7f50cd58dfcbd00c` and manually committed S65.4
`0237579262fd8b86c7b904ecca6cb49f18fd78e1`.

The sole full Phase review P65-FULL-REVIEW-001 r1 passed over 35 owned targets
and 58 reviewed paths, with zero Current Phase Blockers. Its retained admission
and receipt are [full-review-admission-001.json](../../.codex-workflow/phases/PHASE-65/full-review-admission-001.json)
and [full-review-receipt-001.json](../../.codex-workflow/phases/PHASE-65/full-review-receipt-001.json).
Review ledger revision 11 records ten entries/FULL1 before the parent's following
release M0 amendment; eight entries/FULL0 is the historical S65.4 Step acceptance
snapshot on 2026-10-06.

Actual repository-full `sbt --batch test` passed 3,074 tests in 211 completed
suites, with zero failures or aborted suites and nine pre-existing canceled
cases. SBT/wrapper exit 0 and `lock=released` are retained in
[release-full-validation-001.json](../../.codex-workflow/phases/PHASE-65/release-full-validation-001.json),
invocation P65-RELEASE-FULL-ATTEMPT-001. The actual summary is
`/private/tmp/skill.cncf.d/a-5604920c-ea8e-4321-80bc-8596e01d2550/34334-20261006T034510Z.summary.json`;
the corresponding log is `34334-20261006T034510Z.log` in that same directory.

Accepted product evidence remains unit24, all six real-CML SDK products and
live007's two original MCP HTTP200 calls, complete actual typed-response
roundtrips, genuine {} omission, marker echo, counts 5/4/1, CAR/SAR readiness
and strict owned cleanup. Fixture-catalog is ready; overall remains degraded.
The full review could not independently inspect the historical S65.3 raw16
SDK report files: retained native metadata and SBT logs remain, and current31
S65.4F reports plus live007 were inspected. No retrospective replacement
success is claimed. Earlier failures and original raw retention limitations
remain historical evidence. No clean rebuild, separately installed Dart 3.0,
clean upstream release/source-JAR equivalence, all-source readiness, external
UI completion, push, publication, deployment or version conversion is claimed.
The exact existing HYG-P65-S654-MODELER-LOCAL-NAMING-001 record remains in its
committed canonical [Hygiene journal](../../docs/journal/2026/10/2026-10-05-phase-65-hygiene-follow-up.md).

Closure binding is P65-RELEASE-CLOSURE-001 r1. The distinct parent manual release commit records
this closure; its native revision is recorded in Git history. The final commit
must carry `Phase-Closure-Binding: P65-RELEASE-CLOSURE-001@1`; no release SHA
is invented in this projection.
