# Phase 44: SimpleModeler Concurrent Generation Isolation

Status: COMPLETE

Plan date: 2026-09-02

Development item: DEV-015

Dependencies:

- the accepted Phase 43 closure;
- the SimpleModeler Scala-generation boundary used by Cozy;
- the failed Cozy Hygiene Resolution Batch final validation receipt
  `68771-20260901T211250Z`; and
- the historical causal diagnosis confirmed by focused validation and the serialized
  nonparallel full-suite receipt `85815-20260901T214627Z`.

## Accepted Execution Decision

`P44-VAL-001` — accepted 2026-09-02: after focused SimpleModeler validation,
Phase 44 may run exactly one `publishLocal` for the existing
`1.1.26-SNAPSHOT` development coordinate so Cozy can resolve and test the
changed bytes. This is local validation input only. Remote publication, upload,
push, and release-coordinate reuse remain prohibited.

`P44-REVIEW-001` — accepted 2026-09-02: P44-S1 is a protected Phase-wide
implementation unit. It does not take a lightweight Step review; after
implementation and proportional validation, Phase 44 receives exactly one
independent Terra xhigh Phase review before release. This does not waive the
mandatory normal-parallel Cozy full suite or focused validation.

`P44-VAL-002` — accepted 2026-09-02: the one-time `P44-VAL-001`
`publishLocal` supplied the pre-`P44-S1-DOWN-001` bytes. One further local
`publishLocal` of the unchanged `1.1.26-SNAPSHOT` coordinate is permitted so
Cozy can validate the identity-preserving current bytes. It remains local-only;
remote publication, upload, push, and release-coordinate reuse are prohibited.

## Goal

Make SimpleModeler generation safe for concurrent use in one JVM. Each
generation request must resolve declared types from its own model without
observing, clearing, or replacing another request's type-resolution state.

This is both a Cozy test-isolation requirement and a future operational
requirement. A Web or service-hosted Cozy process must be able to serve
independent generation requests concurrently without serializing the whole
application or corrupting generated output.

## Confirmed Cause

Before Phase 44, `ScalaRealmTransformerBase.transform` cleared and repopulated
the process-global `ScalaModelTransformer` object registries before generation.
That historical shared-state path allowed parallel transformations to replace
each other's declared types. The observed `ModelerScalaGenerationSpec` failure
was the direct result: the `Exhibition` generator could not resolve its nominal
scalar types and emitted an empty `_store_record_attributes` vector.

The same pre-Phase-44 Cozy tree and dependency set passed the focused
specification and all 1,677 tests when test-suite parallel execution was
disabled. Therefore the incident was a shared-state race, not a
persistence-metadata classification or datastore migration problem.

## Recorded Validation Evidence

These receipts record the validation runs that preceded `P44-REPAIR-001`.

- Upstream focused: 4 passed — `27212-20260901T225802Z`.
- Upstream compatibility: 24 passed — `28193-20260901T225942Z`.
- Initial local publish: `29424-20260901T230110Z`.
- Downstream focused pre-repair: 29/30 passed — `30791-20260901T230244Z`.
- Post-context-repair upstream focused: 4 passed — `34282-20260901T230634Z`.
- Final local publish: `43791-20260901T232630Z`.
- Downstream focused: 30 passed — `44635-20260901T232747Z`.
- SimpleModeler full: 49 tests / 15 suites / 0 failures —
  `45903-20260901T232927Z`.
- Cozy normal-parallel full: 1,677 tests / 126 suites / 0 failures —
  `47273-20260901T233048Z`.

## Final Closure Evidence

`P44-REPAIR-001` restored the historical deterministic short-name fallback
while keeping declared-type resolution immutable and request-local. Its fresh
focused re-review passed. Final validation then passed with:

- SimpleModeler full: 50 succeeded / 15 suites / 0 failures —
  `76825-20260902T002448Z`.
- Cozy normal-parallel full: 1,677 succeeded / 126 suites / 0 failures —
  `77777-20260902T002641Z`.
- The unblocked Hygiene Resolution Batch final normal-parallel gate: 1,677
  succeeded / 126 suites / 0 failures —
  `P44-HYG-FINAL-VAL-001` / `86195-20260902T004624Z`.

The accepted Phase-wide Terra xhigh review found three bounded issues;
`P44-REPAIR-001` resolved them and the required fresh focused re-review passed.
The HYG final review then found only version-header maintenance, whose
comment-only repair and fresh focused re-review also passed. No remote
publication, upload, push, deployment, or external-driver mutation occurred.

## P44-01: Concurrent Generation Contract and Isolation Design

Stage Status:

- Current status: COMPLETE
- Owner: SimpleModeler generation architecture
- Update rule: complete only when request ownership, lifetime, nesting,
  failure cleanup, and concurrent visibility rules are frozen by Executable
  Specifications and accompanying design/spec records.

- Define one generation request as the authority for its model object/type
  registry and resolution context.
- Remove semantic dependence on a process-global registry whose contents are
  cleared and replaced for every transform.
- Prefer an explicit immutable request-scoped context passed through
  transformation and generation. A scoped implementation is acceptable only
  when it also proves isolation, nesting, exception cleanup, and the execution
  model required by future service hosting.
- Preserve existing generated-source semantics and declared-type resolution
  behavior for a single request.
- Specify that application-wide serialization and disabling Cozy test
  parallelism are not accepted completion mechanisms.

## P44-02: SimpleModeler Isolation Implementation

Stage Status:

- Current status: COMPLETE
- Owner: `/Users/asami/src/dev2025/simple-modeler`
- Update rule: complete only when the authoritative SimpleModeler generation
  path has no cross-request registry mutation and its existing focused
  generation specifications remain compatible.

- Implement request-owned declared-type lookup in the authoritative
  SimpleModeler transformer/generator path.
- Ensure two simultaneous transformations of different models cannot clear,
  overwrite, or resolve against each other's objects.
- Preserve deterministic output for sequential, nested, repeated, and failed
  transformations.
- Add focused upstream regression coverage for nominal datatypes, values,
  inheritance/reference resolution, and failure cleanup.
- Publish or consume only the normal verified SimpleModeler development
  coordinate required for downstream Cozy validation; do not copy the fix into
  Cozy.

## P44-03: Concurrent Executable Specification and Cozy Acceptance

Stage Status:

- Current status: COMPLETE
- Owner: SimpleModeler concurrent acceptance and Cozy downstream validation
- Update rule: complete only when direct concurrent generation, normal
  parallel Cozy testing, and a focused independent Phase review pass on the
  accepted combined tree.

- Run two or more distinct model generations concurrently in one JVM and
  prove that each output contains only its own declared-type and persistence
  metadata.
- Repeat the concurrent scenario sufficiently to catch scheduling-dependent
  registry replacement and preserve the original failure as regression
  evidence.
- Run the focused SimpleModeler and Cozy Modeler generation specifications.
- Run the full Cozy suite with its normal parallel-execution setting. A serial
  full-suite pass is supporting evidence only and cannot close the Phase.
- Record future Web/service hosting as an admitted consumer of the concurrent
  generation contract without introducing a Web endpoint in this Phase.
- Unblock the 2026-09-02 Hygiene Resolution Batch only after a fresh normal
  parallel Cozy suite succeeds; its physical-split Hygiene scope remains
  unchanged.

## Exclusions

- Changing persistence metadata classification or datastore record semantics.
- Disabling `Test / parallelExecution`, serializing the whole Cozy process, or
  adding a Cozy-local lock as the permanent repair.
- Implementing the future Cozy Web/service endpoint itself.
- Visual Page, Media, Storyboard, Logical UI, BoK, PDF, publication, or
  deployment behavior.
- Push, publication, upload, deployment, or external-driver mutation.

## Completion Criteria

Phase 44 completed with SimpleModeler owning declared-type resolution per
generation request; concurrent different-model transformations isolated and
deterministic; sequential and failure-path compatibility intact; the original
Cozy Modeler scenario passing under normal suite parallelism; focused
upstream/downstream coverage, full normal-parallel Cozy validation, and
independent review passing; and the accepted contract ready for later
Web/service-hosted concurrent generation. This terminal record becomes
authoritative in the accepted local Phase closure commit.

## References

- `docs/phase/phase-44-checklist.md`
- `docs/journal/2026/09/2026-09-02-hygiene-resolution-batch-handoff.md`
- `src/test/scala/cozy/modeler/ModelerScalaGenerationSpec.scala`
- `/Users/asami/src/dev2025/simple-modeler/src/main/scala/org/simplemodeling/SimpleModeler/transformer/ScalaRealmTransformerBase.scala`
- `/Users/asami/src/dev2025/simple-modeler/src/main/scala/org/simplemodeling/SimpleModeler/transformer/scala/ScalaModelTransformer.scala`
- `/Users/asami/src/dev2025/simple-modeler/src/main/scala/org/simplemodeling/SimpleModeler/generator/scala/Scala3ClassGeneratorBase.scala`
