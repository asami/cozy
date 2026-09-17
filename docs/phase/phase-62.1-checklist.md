# Phase 62.1 Checklist: StateMachine API/SPI and Durable ActionExecution ABI

Phase status: CLOSED
Updated: 2026-09-17
Ledger for: [Phase 62.1](phase-62.1.md)
Predecessor: [Phase 62](phase-62.md)
Successor ledger: [Phase 62.2](phase-62.2-checklist.md)
Repository-full validation: aggregate deferred to Phase 62.3

This is the sole completion ledger for WFL-62-02 and WFL-62-03. It consumes the Phase 62
source/lowering handoff and produces the ABI handoff for Phase 62.2. Both stages are closed;
the repository-full suite remains aggregate-deferred to Phase 62.3.

## WFL-62-02: StateMachine API/SPI Contract

Stage Status:
- Current status: DONE
- Owner: Cozy StateMachine / generated ABI owner
- Update rule: Mark DONE only after StateMachine has one reusable Provided API / Required SPI contract consumed by Workflow without a parallel Workflow model.

- [x] Define/project StateMachine Provided API operations with stable identity and typed input/result.
- [x] Define/project StateMachine Required SPI operations with stable identity and typed input/result.
- [x] Preserve generic Context, Completion, Evidence, capability/constraint metadata required by an SPI operation.
- [x] Permit local/direct, deterministic test/mock and external provider placement without duplicating State/Guard/Operation/Result semantics.
- [x] Ensure Workflow projects/reuses StateMachine API/SPI rather than an independent interface model.
- [x] Preserve identity/type metadata for future assemble `SPI -> API` binding and caller-side API projection.

Evidence: `StateMachineApiSpi.fromWorkflow` is the sole Workflow projection;
`StateMachineProviderBinding` retains only Required SPI -> Provider identity.
The focused `StateMachineApiSpiSpec` and `WorkflowCmlSpec` serial-SBT run passed
32 tests, including the generated fail-closed property; the independent
acceptance review found no current boundary blocker.

## WFL-62-03: ActionExecution and Durable Continuation ABI

Stage Status:
- Current status: DONE
- Owner: Cozy StateMachine / generator owner
- Update rule: Mark DONE only after generated ABI represents internal completion, external suspension, failure and typed resume without mode semantics.

- [x] Define the closed `ActionExecution = Completed | Suspended | Failed` StateMachine-general ABI.
- [x] Define typed Completed(Result), Failed(Error) and Suspended(Continuation) payload semantics.
- [x] Define Continuation, ContinuationResult, ContextBundle, ContextReference, ContextSnapshot, CompletionContract and EvidenceContract.
- [x] Require instance/run identity, expected revision, minimum context, completion/evidence and typed resume result.
- [x] Generate fail-closed stale-result semantics through ContextSnapshot.
- [x] Exclude Workflow-wide Orchestration/Continuation mode and semantic InvocationBinding from canonical ABI.

Evidence: the pure `ContinuationResumeValidator` rejects each protected stale,
identity, descriptor, Context, constraint, completion and evidence mismatch by
a closed rejection value. Its fixed cases and ScalaCheck generated divergence
property passed in the focused 32-test serial-SBT run. The 28-test Composite
StateMachine compatibility accumulator also passed. The independent focused
re-review and full Phase acceptance review were clean.

## Aggregate-deferred release evidence

`repository_full_suite=deferred-not-run`. The Phase ran focused serial-SBT
validation, independent repair re-review and final acceptance review, then
closed its two completed stages. The aggregate repository-full suite belongs
only to Phase 62.3 for `PHASE-62 -> PHASE-62.1 -> PHASE-62.2 -> PHASE-62.3`.
No Phase 62.2 or Phase 62.3 implementation is included in this release.
