# Phase 50 → Phase 50.1 handoff

Owner: Cozy PHASE-50.1 upon a fresh explicit invocation. Update rule: extend
the accepted shared assets and canonical matrix; retain source-input identity,
candidate/accepted separation, ordinary serial validation/review and native
history. Status: READY as a committed PHASE-50 release handoff; PHASE-50.1
remains planned and its 59 original outcomes remain open.

## Starting authority

Consume [Phase 50 closure](phase-50-closure-ledger.md) and its original base,
two native accepted Step commits and sole independent Phase review. The
distinct release is identified by `Phase-Closure-Binding: P50-CLOSURE-001@1`:

```sh
git log --format='%H' --fixed-strings --grep='Phase-Closure-Binding: P50-CLOSURE-001@1'
```

The release audit trailer and parent post-commit receipt carry actual native
history. This handoff neither starts the successor nor grants full target-ready
acceptance or Phase 51 readiness. Commits remain parent manual local commits.

## Frozen source and inputs

Use the existing test fixture
`src/test/scala/cozy/ui/CozyLogicalUiSalesOrderFixtures.scala`:
`candidate()` creates the genuine candidate, screen projection and semantic
projection via the existing Phase 43 factories; it makes no acceptance decision.
`compactSuccess` supplies the declared initial/success records;
`compactWebScenario` translates that complete scenario for the producer.

The actual [production receipt](../evidence/phase-50/compact/compact-preview-receipt.json)
is authoritative for the full tuple and scenario. These are existing product
input identities, not workflow-control hashes or file-immutability certificates.

| Declared product identity | Retained value |
| --- | --- |
| `candidateIdentity` | `sha256:3e5a37efbcdf29e7e950118bbf5763f4e2bf6b76c8d80011a5896877af3d7c47` |
| `consumedInputIdentity` | `sha256:9199b0a67e6581c3a2f24a8ce24675499226f3699a21a9fc772156fc01a121f0` |
| `projectionIdentity` | `sha256:b1418e811aac95d9c4627dd1813902a108d1b9532f5f6e6c9615ff55a4fbdc1a` |
| `semanticProjectionIdentity` | `sha256:1b712be932304f7935488b0c91a64e65e15086e579cb761cde8bbccc87b7f449` |

Fixture: `sales-order-compact-v1`; coverage: `P50-COMPACT-NORMAL-001`.
Component: `org.example.sales.SalesOrder@1.0.0`, with exports `entity`,
`aggregate`, `service`, `operation`, `value`, `datatype`, `view`, `workflow`,
`powertype`, `state-machine`. UI use case/step: normal
`ui-confirm-sales-order/confirm`. Screen interactions in order:
`order-list/entry`, `order-list/to-detail`, `order-detail/select`,
`order-detail/invoke`, `order-detail/observe`. Selection uses Powertype variant
`awaiting-confirmation`; confirmation consumes the exact Operation and
StateMachine bindings with the existing Aggregate mutation admission.

Both records declare order `SO-001`, label `Sales order SO-001` and opaque
amount `100.00`: initial domain state `draft`, supplied successful domain state
`confirmed`, separate successful UI state `succeeded`. Workflow state stays
separate. Confirmation displays an explicit simulation; no domain/backend
executor or synthetic semantic acceptance is introduced.

## Producer and executable evidence

Candidate preview: `src/main/scala/cozy/ui/CozyLogicalUiReview.scala`;
compact producer: `src/main/scala/cozy/ui/CozyLogicalUiCompactWeb.scala`.
The compact schema is `cozy.logical-ui-compact-web.v1` version 1 and renderer
`cozy.logical-ui-compact-web.renderer.v1`, profile `compact`, placement
`navigate-to-detail`, `simulated=true`. Candidate review uses
`cozy.logical-ui-candidate-review.v1` and its version-1 renderer.
Retain exact full tuple/scenario/bindings and deterministic currentness,
escaping, path/preflight rejection and per-file atomic replacement obligations.

`CozyLogicalUiCandidateReviewSpec` proves genuine candidate authority and input
binding; `CozyLogicalUiCompactWebSpec` proves deterministic projection,
currentness, escaped output and writer behavior; the existing Phase 43/74
acceptance/UI/semantics/runtime specifications protect the reused contract.
S50.1 passed seven suites / 61 tests; S50.2 passed eight suites / 74 tests.
These are actual historical receipts, not successor validation results.
For affected compact work, the parent-selected focused argv used was:

```text
--batch
testOnly cozy.ui.CozyLogicalUiCompactWebSpec cozy.ui.CozyLogicalUiCandidateReviewSpec cozy.ui.CozyLogicalUiReviewSpec cozy.ui.CozyLogicalUiPhase43AcceptanceSpec cozy.ui.CozyLogicalUiSpec cozy.ui.CozyLogicalUiSemanticsSpec cozy.ui.CozyLogicalUiRuntimeSpec cozyruntime.CozyLogicalUiRuntimeAcceptanceSpec
```

Run any newly required SBT through the registered `cncf_command_runner` and
shared `cncf-sbt-serial-execution` wrapper, waiting for `lock=released`.
Reassess actual changed inputs; a native commit or handoff format change alone
does not require repeating applicable validation.

## Compact outputs and actual browser proof

Retain [app](../evidence/phase-50/compact/index.html),
[structural HTML](../evidence/phase-50/compact/review.html),
[full receipt](../evidence/phase-50/compact/compact-preview-receipt.json),
[portable observations](../evidence/phase-50/compact/browser-observations.json)
and [six screenshots plus browser-entry prerequisites](../evidence/phase-50/compact/README.md).
The actual Chrome 154.0.8037.94 run at 390 × 844 observed list, selected,
detail, confirmation-ready, simulated-success and structural-review, without
horizontal overflow in the compact stages. The owned-session receipt admitted
readiness, client completion and awaited cleanup; no external/mutating request
or browser error occurred.

The existing `scripts/test/phase50/run-compact-browser.mjs` requires installed
Node, Playwright and Chrome and five flag/value pairs, each with canonical
absolute paths: `--site`, `--receipt`, `--evidence`, `--playwright`, `--browser`.
Use a task-private site containing the two selected HTML files and a matching
receipt outside that served directory; select a fresh evidence directory.
The driver validates complete production identities/scenario, owns its temporary
profile and loopback server, and cleans them up. Route it via the typed
`cncf_runtime_session_runner`; do not launch a persistent preview or install
browsers merely to recreate retained evidence.

The sole partial canonical coverage row remains
[specification section 11](../spec/logical-ui-model.md#11-shared-salesorder-compact-success-contract),
`P50-COMPACT-NORMAL-001`. Extend that row/matrix with newly proved outcomes;
do not create another semantic authority or mark broad successor rows complete
from compact normal simulation alone.

## Remaining responsibility

The complete [59-row successor checklist](phase-50.1-checklist.md) stays open.
PHASE-50.1 owns detailed input/local validation, loading/failure/recovery/reset/
replay, medium/expanded behavior, remaining metadata/diagnostics and feedback,
complete target-ready acceptance and the aggregate full suite. It must obtain
its own Step/Phase reviews and distinct release before the unchanged Phase 51
dependency is satisfied.

The reciprocal declaration is unchanged: `split_full_test_policy=final-only`,
`split_full_validation_method=sbt-full-suite`, bootstrap `none`, owner
`PHASE-50.1`, sequence `PHASE-50` → `PHASE-50.1`, direct dependency
`phase-50.md`. PHASE-50 reports `repository_full_suite=deferred-not-run`;
aggregate assurance remains pending. Execute the one full serialized Cozy suite
at the final owner boundary; never relabel focused or browser success as full
success. Shared Phase-index/strategy synchronization remains deferred concurrent
planning and is recorded separately from canonical Phase closure.
