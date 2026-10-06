# Phase 65: CBD Support Dart Model and API Code Generation

Stage Status:
- Current status: IN_PROGRESS
- Owner: Cozy PHASE-65 workflow parent (`/root`)
- Update rule: Update against [the original checklist](phase-65-checklist.md) after accepted evidence; narrative authoring and prerequisite success do not close the Phase.
- Current step: S65.4G actual unit24 and [live007 product proof](../../target/phase65/s654/runs/live-gru8e1bo/result.json) PASS: CAR/SAR readiness and both MCP HTTP200, full typed responses/genuine {}/marker, fixture-catalog ready, overall degraded, strict cleanup. Actual251/14/31/six real-CML products/unit20 reused; live006 CAR200/SAR404 failure retained. S65.4 independently accepted for its local Step commit by P65-S654-REVIEW-001 r1 PASS, zero blockers and one separate Hygiene; seven original boxes checked and final Phase closure box unchecked. Full Phase review, repository-full tests and distinct manual release commit remain pending.

Status: IN_PROGRESS

S65.4 is independently accepted for its local Step commit.
P65-S654-REVIEW-001 r1 from `/root/p65_s654_review_001` returned PASS with zero
Current Boundary Blockers and one separate nonblocking Hygiene item,
HYG-P65-S654-MODELER-LOCAL-NAMING-001, recorded in the
[Hygiene follow-up](../journal/2026/10/2026-10-05-phase-65-hygiene-follow-up.md).
Independent Step acceptance recorded eight review entries, with zero Phase full reviews. DART-08 and DART-09
are completed in accepted S65.4; no external UI integration is claimed.
Native acceptance history records local Step commits.
Full Phase review, repository-full tests and distinct manual release commit remain pending.

Plan date: 2026-09-15
Rebased from the GitHub Phase 55 planning record: 2026-09-17
Primary downstream consumer: Textus CBD Support

## Purpose

Add a general Dart target to Cozy that projects canonical model and operation
IR into compilable, null-safe Dart model and API-boundary code for Textus CBD
Support. The target lets the CBD Support client use the same modeled contracts
as its Scala/CNCF-side counterparts where those contracts are available.

This is a reusable client-contract foundation, not a Flutter widget generator
or a CBD Support application implementation. Consumer-specific names and
handwritten transport behavior remain outside generator implementation.

The linked Dart proposal and KnowledgeHub journal are retained as the imported
GitHub planning input. This Phase supersedes their active Phase 55 assignment
and KnowledgeHub-specific initial-consumer choice: the current planned target
is Textus CBD Support, while completed local Phase 55 remains the site-context
media-registration closure.

## Work stack

| ID | Outcome | Status |
| --- | --- | --- |
| DART-01 | Inventory Cozy IR, existing generation targets, operation metadata, and CBD Support consumer contracts without creating a shadow Dart IR. | completed in accepted S65.1 |
| DART-02 | Freeze deterministic Dart mappings for required/optional values, identifiers, collections, enums, variants, naming, packages, and imports. | completed in accepted S65.1 |
| DART-03 | Generate immutable, typed Dart model classes with explicit required/optional constructor semantics. | completed in accepted S65.2 |
| DART-04 | Generate enums and sealed variants wherever the source guarantees closure. | completed in accepted S65.2 |
| DART-05 | Generate JSON codecs that preserve nullability, discriminators, names, and round-trip semantics. | completed in accepted S65.2 |
| DART-06 | Generate typed request/response/error DTOs and operation interfaces from canonical operation metadata. | completed in accepted S65.3 |
| DART-07 | Emit deterministic, consumable Dart package layouts with analyzer-clean output. | completed in accepted S65.3 |
| DART-08 | Prove one real Textus CBD Support consumer path uses generated contracts. | completed in accepted S65.4; actual unit24 and [live007 product proof](../../target/phase65/s654/runs/live-gru8e1bo/result.json) PASS: both original scenarios, readiness/MCP HTTP200, full typed responses/genuine {}/marker, counts5/4/1, fixture-catalog ready/overall degraded, strict cleanup; actual251/14/31/six real-CML products reused; historical live006 CAR200/SAR404 failure retained; Phase closure remains pending |
| DART-09 | Document reuse by later CBD Support UI work and prohibit a parallel shadow model. | completed in accepted S65.4; later UI reuse documented without external UI integration |

## Current Step and evidence correspondence

S65.1 accepted the [normative contract](../spec/dart-model-api-generation.md)
and [source/design inventory](../design/dart-model-api-generation.md) at native
commit `f4a6e3b0f41fd929fba085664ae958330b82cff0`, after independent review and
M0 closure. DART-01/02 and the first two original checklist items are complete.

S65.2A provides the in-memory data-stage generator, immutable models,
classification representations and technical JSON helpers. The
[data-interface specification](../../src/test/scala/cozy/modeler/DartModelGenerationSpec.scala)
and [emitted-runtime specification](../../src/test/scala/cozy/modeler/DartModelRuntimeSpec.scala)
provide executable evidence for DA-01..12. Main and Test compilation passed,
and `testOnly cozy.modeler.DartModelGenerationSpec cozy.modeler.DartModelRuntimeSpec`
completed both suites with 21/21 tests passed, no skips or aborts, in post-repair
validation attempt 006. Actual emitted Dart SDK analysis/runtime and compiler
contract checks succeeded. Independent focused re-review 006 accepted the
complete Step after resolving `CPB-P65-S652-001`, the primitive member-name
collision admission defect. DART-03..05 and both model/serialization checklist
items are complete. S65.2 is accepted at native commit
`716d208ce8505746ac03a3cc3606299f5c4e10e4`.

S65.3A authors the pure [package entry](../../src/main/scala/cozy/modeler/DartPackageGenerator.scala),
canonical [operation selection](../../src/main/scala/cozy/modeler/DartOperationProjection.scala),
[API renderer](../../src/main/scala/cozy/modeler/DartApiRenderer.scala) and explicit
`modeler dart` facade. The [generation specification](../../src/test/scala/cozy/modeler/DartPackageGenerationSpec.scala),
[independent SDK consumer specification](../../src/test/scala/cozy/modeler/DartPackageRuntimeSpec.scala)
and [CLI specification](../../src/test/scala/cozy/modeler/DartModelerServiceSpec.scala)
are authored evidence for DA-13..19, including opt-in qualified canonical
bindings and per-service source leaf ownership. The representative fixture is
[canonical CML](../../src/test/resources/modeler/dart-api.cml), and its
[separate Dart consumer](../../src/test/resources/modeler/dart-api-consumer.dart)
imports generated contracts without authoring DTOs. Focused validation attempt
004 passed Main and Test compilation and all twelve suites: 195/195 tests
succeeded, with zero failures or aborted/canceled/ignored/pending tests. SBT
and wrapper exited 0, and the serial lock was released. Actual SDK evidence
contains sixteen direct command reports across the typed positive consumer,
intended negative type diagnostics and all-void package scenarios; the negative
analyzer was intentionally rejected. Dart SDK 3.13.4 exercised `@dart=3.0` source
mode; this is not evidence from a separately installed Dart 3.0 SDK.
The first complete independent protected Step review 001 returned PASS, with
no Current Boundary Blockers, Hygiene or Development Candidates. It accepted
the malformed canonical-model error guard and valid-empty/CLI conformance.
S65.3 is independently accepted at native commit
`770bf6b380a292c027fa059c7f50cd58dfcbd00c`.
DART-06/07 and both API/package checklist items are complete.
Historically, the two real CBD/Phase closure checklist items remained unchecked at S65.3. At S65.3
acceptance, successful live real CBD/S65.4 proof remained pending; actual S65.4G
[live007 product proof](../../target/phase65/s654/runs/live-gru8e1bo/result.json)
subsequently passed. S65.4 is now independently accepted for its local Step
commit; the real CBD checkbox is checked and the final Phase closure box remains
unchecked. Full Phase review, repository-full tests and distinct manual release commit remain pending.

S65.4A authors the [independent real CBD consumer](../../examples/dart-cbd-consumer/README.md),
the [preparation script](../../scripts/test/phase65/prepare-cbd-consumer.py) and
[owned live acceptance script](../../scripts/test/phase65/run-cbd-consumer.py).
The consumer implements the generated `CbdSupportCbdRetrievalApi` through an
injected function and uses generated request/response codecs, named scalar
wrappers and typed integer counts without a second domain model. Preparation
copies actual tracked CBD working-tree sources into fresh ignored scratch,
records native/dirty attribution and appends only private launcher/classpath
settings. The authored acceptance procedure requires a real built CAR and
standalone SAR, actual declared CNCF runtime, owned loopback bind/readiness,
offline Dart analysis, explicit and omitted detail live calls, structural typed
roundtrips and terminal owned cleanup. Its scenario observations record Given
real source/artifact/runtime, When generated requests travel through MCP and
typed responses are consumed, Then actual wire/typed/count correspondence.
Failures leave DA-21/DART-08 unresolved without substituted success evidence.
The S65.4A authoring Slice did not execute or independently accept these
programs. Later UI reuse is documented; no UI integration is claimed.

Subsequent preparation generated the private CBD CAR and successfully exported
the dependency-only runtime classpath after the separately accepted
`Runtime / externalDependencyClasspath` repair. The historical failed
`dependencyClasspath` preparation and its raw-submission evidence limit remain
retained. Actual Dart generation from the unchanged copied CBD CML then failed
because the legacy QUERY request was absent from canonical data projection.

S65.4B authors the Dart-local
[canonical input bridge](../../src/main/scala/cozy/modeler/DartCanonicalInputProjection.scala)
and wires it into the existing package entry before data and binding projection.
It joins exact admitted legacy names to unchanged original QUERY/COMMAND DTO
Sections and uses the existing ValueClass source factory, preserving package,
schema, metadata and location. Duplicate names, missing/ambiguous source and
unsuccessful factory results reject; existing VALUE and normalizer precedence
remain authoritative. Non-Dart builder behavior is unchanged. The
[legacy-input executable specification](../../src/test/scala/cozy/modeler/DartLegacyInputGenerationSpec.scala)
authors adjacent Given/When/Then and ScalaCheck observations for canonical
identity, codecs, source rejection, precedence, compatibility and deterministic
selection. An earlier `newSpec` compilation failed and remains historical
evidence. Current-tree focused validation subsequently passed all 13 suites,
217/217 tests, retaining sixteen SDK command reports, zero SBT/wrapper exits
and `lock=released`. Fresh actual-source SDK generation passed with all six
products. The first live attempt failed before readiness, ran zero scenarios
and completed owned cleanup; it provides no live CBD/Dart consumer proof.

S65.4C authors a Cozy-owned private canonical JSON SAR from the real CAR's
schemaVersion integer 3 and exact `namespace=org.simplemodeling.textus`,
`id=CbdSupport`, `version=0.1.0-SNAPSHOT`. Admission fails on
missing/malformed/legacy-only/conflicting identity before Java/fixture startup.
The SAR contains only `subsystem-descriptor.json`; the original copied YAML
descriptor/SAR and failed result remain read-only, and the CAR is copied
unchanged. Existing authentication convention/fallback privilege remain
disabled, with fixed local subject `cbd-standalone-review`, role `reviewer`,
security level `user`, installation `cbd-standalone`. Explicit
`textus.subsystem.user-mode=standalone` retains the selected standalone subsystem;
process/run receipts retain derivation and effective mode. The scripts now live
under `scripts/test/phase65`, including the
[CAR/SAR behavioral regression](../../scripts/test/phase65/test_run_cbd_consumer.py).
Actual CAR/SAR regression passed 12/12 with zero failures/errors in
`target/phase65/s654c-unit-result.json`. Historical live001 retains its
pre-readiness deployment failure. Live002 (`s654/runs/live-2bqktlyp`) reached
the owned successful bind, HTTP 200 OpenAPI and successful `tools/list` with
exactly one `org.simplemodeling.textus.CbdSupport.CbdRetrieval.status`.
VAL-P65-S654C-005 failed before scenarios because the private locator omitted
the namespace; zero scenarios ran and owned Java/fixture cleanup succeeded.
The corrected private MCP `params.name` uses that fully qualified identity;
canonical operation selection `CbdSupport.CbdRetrieval.status` and generated
API types remain unchanged. Live003 (`s654/runs/live-1ymsxpks`) reached owned
readiness, SDK offline pub/analysis and consumer pub, then failed consumer
analysis on generated `Object?` request encoding at the typed transport
boundary. Zero scenarios ran. TERM then `Operation not permitted` left
cleanupfalse; the failing syscall is unknown. Later scoped probes observed
PID/group 9834 absent as separate evidence only, without repairing that receipt
or identifying cause. Failed runs/raw evidence limits remain retained. At that
historical attempt, independent S65.4 Step review/commit, repository-full tests,
full Phase review and final release remained pending. No implementation acceptance
or UI integration was claimed at that attempt.

S65.4D authors a local final request encoding and `Map<String, Object?>` check
before typed transport, raising the named FormatException for a non-object.
Generated codec signatures, wrappers/int types and APIs remain unchanged.
After canonical CAR/SAR admission and before Java version/fixture acquisition,
the harness refreshes only current authored pubspec/bin consumer files into
the exact `target/phase65/s654/consumer` workspace. Safe-path/regular checks
reject input/output/ancestor symlinks; only consumer/bin directories are
created. `../sdk`, `.dart_tool`, source snapshot, original preparation and old
runs remain. Result/Given `consumerPreparation` records the actual two-file
source/destination locators without attributing refresh to original preparation.

Owned group observation uses the selected macOS `/bin/ps -g <group> -o pid=,pgid=`
contract, no shell, capture/text, timeout2s, preserved PATH and `LC_ALL=C`, with
raw argv/start/finish/exit/stdout/stderr or timeout/OSError evidence. Exit1 with
empty streams alone proves absence; exit0 requires empty stderr/nonempty rows
of two positive integers in the owned group. Other results fail by name;
Linux universality and inability-as-absence are not claimed. Cleanup always
attempts native owned TERM, polls/reaps during 15s grace, KILL unless absence
was observed, always wait10s and final absence5s. Stage/errors persist through
later observations; ESRCH proves only an absent signal target. Success requires
wait-observed child exit, observed groupGone, no errors and fixture close/join.
Query output supplies no signal targets or permission recovery.

The twelve existing CAR/SAR regressions remain. Exactly six mock-boundary
behavioral methods now cover empty group; leader/children and child-only group;
malformed/mismatched results; timeout/permission raw evidence; cleanup denial/
query failure with best-effort wait; and current two-file refresh/provenance,
retained output and symlink rejection. Adjacent Given/When/Then is authored in
each method/subtest. The next unit execution writes actual counts/status to
fresh `s654d-unit-result.json`, fails on no tests and preserves historical unit12.
Actual execution passed unit18 and live004 SDK/consumer pub/analysis, first
MCP200 and strict cleanup; full response decode and the second call remain
unresolved at that historical attempt as recorded below. Both final original
checklist boxes, independent Step review/commit and the separate full Phase/release
gates remained open then; no UI integration or acceptance was claimed at that attempt.

S65.4E authors the public sealed Scala 2
[JSON field profile](../../src/main/scala/cozy/modeler/DartJsonFieldStyle.scala),
appended typed model/package options and the Dart-only `--json-field-style`
property. Default/explicit `source` retains exact original keys; explicit
`cncf-snake-case` mirrors the selected CNCF 0.5.3-SNAPSHOT context naming,
including separator normalization and each-uppercase acronym splitting
(`URLValue` to `u_r_l_value`). Invalid/empty/whitespace styles fail through
structured conclusions before generation/saving. Projection keeps original
canonical objects, Dart symbols and metadata identities and rejects transformed
key collisions before symbol admission/emission. Renderer consistently selects
field encode/decode/wire metadata; scalar/discriminator/API/runtime contracts
retain source semantics.

The [profile executable specification](../../src/test/scala/cozy/modeler/DartJsonFieldStyleSpec.scala),
[CLI matrix](../../src/test/scala/cozy/modeler/DartModelerServiceSpec.scala) and
[independent Dart consumer scenario](../../src/test/scala/cozy/modeler/DartPackageRuntimeSpec.scala)
author DA-22 parsed canonical/default/profile/collision/determinism and actual
generated constructor/codec observations. Unit18's actual receipt is
`target/phase65/s654d-unit-result.json`. Actual live004
[result](../../target/phase65/s654/runs/live-nm1w2a4r/result.json) passed SDK/consumer
pub/analysis, generated request, first real MCP200 and strict owned cleanup.
The unchanged actual payload used source_count=5, ready_source_count=3 and
component_count=1; Source decoding required sourceCount and failed. Zero full
scenarios completed and the omitted-detail second call did not run.

The [real procedure](../../examples/dart-cbd-consumer/README.md) now selects
`--json-field-style cncf-snake-case` into fresh `target/phase65/s654/sdk-cncf-snake`,
retaining original `sdk`, unchanged actual CML/Record producer/CAR/SAR/runtime,
preparation and failed runs. The consumer's only dependency is
`../sdk-cncf-snake`. Execution/scenario/Given provenance names that SDK/profile;
fixed comparisons map original typed camel report counts to actual snake fields.
Full generated `decodedResponse == actual payload` remains mandatory for both
original MCP calls with no mutation or model/codec/transport substitute.
S65.4E's actual accumulator passed 249/249 tests in 14 suites with 21 SDK
command reports, zero SBT/wrapper exits and `lock=released`. Fresh generation
from unchanged real CBD CML passed with all six products. Historical 217/217
remains S65.4B evidence; these results do not validate the new S65.4F method.

S65.4F authors `Map<String, Object?> toJsonOmittingNullFields()` on generated
non-scalar data objects. Generated collection-if entries omit only null
`MZeroOne` fields and recurse through required/optional/list data references.
Required keys, nonnull values and empty lists remain; empty objects return a
typed empty map. Scalar wrappers and enum/sealed/open classifications receive
no omission method and retain scalar/discriminator encoding. Default
zero-argument `Object? toJson()` expression and recursive explicit-null behavior
stay unchanged before/after omission calls. Constructor/decode checks, original
identities/Dart symbols, both field profiles and metadata remain. Omitted JSON
decodes to canonical null state; omission re-encoding preserves absence while
default re-encoding restores null keys. Data-only method reservation rejects
canonical field collisions before rendering, with scalar/classification
reservations intact. No map filtering or CLI option is introduced.

The [DA-23 collision behavior](../../src/test/scala/cozy/modeler/DartJsonFieldStyleSpec.scala)
and [independent runtime behavior](../../src/test/scala/cozy/modeler/DartPackageRuntimeSpec.scala)
are authored with actual S65.4F validation recorded below. Direct/package collisions exercise both
profiles. Separate Source/CNCF scratch consumers locally extend ProfileItem
with optional itemNote and compare complete independently spelled JSON for
recursive required/list nulls, nonnull string/scalar/finite values, required/
empty-list continuity, default before/after and decode/restoration.

Actual live005 [result](../../target/phase65/s654/runs/live-96ligvfe/result.json)
retains both real MCP HTTP200 calls and complete generated typed decode/default
re-encode equality with both full actual payloads, explicit marker echo and
strict owned Java/fixture cleanup PASS. The second request was {"detail":null};
the runtime returned detail string "null", failing the original omitted-detail
fixture summary (VAL-P65-S654E-009). This is partial proof. Earlier preparation,
S65.4B/S65.4E compile and live001..004 failures remain retained, along with
raw-submission gaps, SDK CLI platform-input absence and corrected native
attribution limits. Missing historical input is not reconstructed.

The real consumer selects generated omission encoding in request and injected
status modes. Its map return supersedes the redundant object-type guard;
response.fromJson/default toJson remain. The fresh dependency/execution/scenario/
Given SDK is `target/phase65/s654/sdk-cncf-snake-omit`, retaining profile
`cncf-snake-case` and naming `requestEncoding=toJsonOmittingNullFields`. Before
HTTP, exact {"detail": marker} or {} is required; present-null/additional keys
fail, generated evidence is retained and injected/sent maps remain unchanged.
Full actual response equality, actual snake counts, marker/fixture summary and
strict lifecycle cleanup remain mandatory. Two mock-only Python methods author
exact provided/absent envelopes/full typed controlled payloads and pre-HTTP
present-null rejection; they are not real CBD proof. Fresh unit output is
`target/phase65/s654f-unit-result.json`, preserving unit18's receipt.

Actual S65.4F validation passed 251/251 tests in 14 suites, 31 SDK commands
across six independent consumers and one controlled negative, six actual real-CML
SDK products and unit20 PASS. Actual live006 (`target/phase65/s654/runs/live-5epmn_uo`)
passed both real MCP HTTP200 calls, full typed raw-response equality, genuine {}
omission, marker echo and strict cleanup. Its omitted summary reported
`fixture-catalog=degraded`, with actual CAR index200/SAR index404; live acceptance
remained false at that attempt (VAL-P65-S654F-010). Historical249/21/unit18/live005 and all earlier
failures remain retained.

S65.4G authors a per-run private `fixture-input` copy of original catalog inputs
before fixture binding. Safe paths/recursive and ancestor symlink rejection,
fresh absent destination and nonempty regular CAR index/model metadata are
required. Original fixture bytes remain unchanged. An absent SAR index is
generated only in the copy as {"entries":[],"diagnostics":[]}, meaning zero SAR
profiles without schema/schemaVersion, invented metadata or canned status
output. An existing nonempty regular SAR index retains its exact bytes/entries.
Result, server-process and Given `fixturePreparation` expose absolute
`sourceFixtures`, `servedFixtures`, `sarIndex` and boolean `sarIndexGenerated`
separately from source preparation. The server serves the private directory;
CAR then SAR readiness labels use existing bounded JSON HTTP before socket
selection/Java acquisition, preserving actual receipts/raw responses and failure.

Four new adjacent Given/When/Then behavior methods specify missing-SAR private
generation, existing-SAR preservation, named admission failures and mock-only
endpoint order/SAR failure, retaining all twenty existing methods. Relevant
251/14/31 and six real-CML SDK evidence is reused. Actual [unit24](../../target/phase65/s654g-unit-result.json)
passed 24 tests with zero failures/errors, success true and runner exit0.
Actual [live007](../../target/phase65/s654/runs/live-gru8e1bo/result.json) completed
both original scenarios with owned session007 exit0, no timeout/signal/forced
termination, CAR/SAR readiness HTTP200 and both real MCP HTTP200. Private SAR
entries/diagnostics are empty; source fixtures remain read-only with provenance intact.
Exact generated marker and genuinely omitted {} requests passed full actual
typed-response equality and marker echo. Actual counts are source_count=5,
ready_source_count=4, component_count=1; the exact summary is simplemodeling=degraded,
fixture-catalog=ready, empty=ready, local-car=ready, cache-car=ready, so overall
remains degraded. Java28739/group28739 TERM, no KILL/errors, wait143 and two
native group-absence observations, fixture close/thread join and strict cleanup
passed. Complete submitted/native initial/terminal records remain after one
evidence-only continuation, with no rerender/replay; mistaken progress literals
were superseded and historical raw retention limits remain. Product live proof
PASS supplies the real consumer evidence for independently accepted S65.4.
The real CBD checkbox is checked; the final Phase closure box remains unchecked.
Full Phase review, repository-full tests and distinct manual release commit remain pending.

| Internal Step | Checklist group | Phase outcomes | Evidence locator / closure basis |
| --- | --- | --- | --- |
| S65.1: contract inventory and mapping; Slice S65.1A | DART-01 | DART-01, DART-02 | Spec sections 1–12 and acceptance matrix; design canonical inventory and CBD route; accepted native f4a6e3b after independent review and M0 closure. |
| S65.2: immutable models and codecs; Slice S65.2A | DART-02 | DART-03, DART-04, DART-05 | Accepted native 716d208; spec sections 3–8 and DA-01..12; both Dart model suites passed 21/21 in attempt 006 with actual SDK/runtime/compiler evidence; independent focused re-review 006 accepted CPB-P65-S652-001 closure. |
| S65.3: operation/package entry; Slice S65.3A | DART-03 | DART-06, DART-07 | Accepted native 770bf6b; spec sections 8–11 and DA-13..19; package/API/CLI implementation and three executable specifications; focused attempt 004 and actual SDK consumer scenarios passed as detailed above; first complete independent protected Step review 001 PASS. |
| S65.4: real CBD consumption and handoff; Slices S65.4A/S65.4B/S65.4C/S65.4D/S65.4E/S65.4F/S65.4G | DART-04 | DART-08, DART-09 | DA-20..23; actual251/14 suites/31 SDK commands/six real-CML products/unit20 reused; historical live001..006 failures retained, including live006 CAR200/SAR404 degradation and acceptance false at that attempt; actual unit24/[live007 product proof](../../target/phase65/s654/runs/live-gru8e1bo/result.json) PASS, both scenarios/readiness/MCP HTTP200/full typed equality/genuine {}/marker/counts5/4/1/fixture-catalog ready/overall degraded/strict cleanup; DART-08/DART-09 completed in accepted S65.4; independent Step review PASS/zero blockers/one separate Hygiene; full Phase review, repository-full tests and distinct manual release commit pending. |

The Phase outcomes and checklist groups retain their separate original
numbering. Runtime prerequisites are recorded in the design; they do not
constitute completion evidence or remove the real CBD acceptance obligation.

## Required boundaries

- Required and optional source values map to Dart null safety; domain models do
  not degrade into untyped `dynamic` maps.
- Closed source variants retain available exhaustiveness information; open
  extension points are not falsely declared closed.
- Generated API interfaces are typed boundaries, not a required concrete
  transport implementation.
- Phase 54 semantic metadata remains the supplier for rich CBD Support
  semantics; this Phase projects admitted model and operation contracts into
  Dart and must not invent missing semantics.

## Acceptance

- Cozy emits compilable, analyzer-clean Dart from representative canonical IR.
- Type mappings, JSON round trips, enum/variant handling, and typed API DTOs
  preserve the available source semantics.
- The generated package is consumable by a Textus CBD Support client path.
- Later CBD Support UI work reuses the generated model/API layer instead of
  creating a parallel client-local model.

## Non-goals

- Flutter widget generation, state-management, navigation, camera/platform
  plugins, local database work, or synchronization implementation.
- CBD Support-specific handwritten client behavior or arbitrary Dart source
  merge/round-trip editing.
- Replacing Phase 54 as the authority for semantic metadata.

## References

- [Phase 65 checklist](phase-65-checklist.md)
- [Dart model/API generation specification](../spec/dart-model-api-generation.md)
- [Dart model/API generation design](../design/dart-model-api-generation.md)
- [Dart model/API generation proposal](../notes/dart-model-api-code-generation-provisional-specification.md)
- [Dart consumer planning record](../journal/2026/09/2026-09-15-dart-code-generation-knowledgehub-consumer.md)
- [Phase 54](phase-54.md)
