# Phase 62.1 Checklist: StateMachine API/SPI and Durable ActionExecution ABI

Phase status: planned
Updated: 2026-09-17
Ledger for: [Phase 62.1](phase-62.1.md)
Predecessor: [Phase 62](phase-62.md)
Successor ledger: [Phase 62.2](phase-62.2-checklist.md)
Repository-full validation: aggregate deferred to Phase 62.3

This is the sole completion ledger for WFL-62-02 and WFL-62-03. It consumes the Phase 62
source/lowering handoff and produces the ABI handoff for Phase 62.2.

## WFL-62-02: StateMachine API/SPI Contract

Stage Status:
- Current status: OPEN
- Owner: Cozy StateMachine / generated ABI owner
- Update rule: Mark DONE only after StateMachine has one reusable Provided API / Required SPI contract consumed by Workflow without a parallel Workflow model.

- [ ] Define/project StateMachine Provided API operations with stable identity and typed input/result.
- [ ] Define/project StateMachine Required SPI operations with stable identity and typed input/result.
- [ ] Preserve generic Context, Completion, Evidence, capability/constraint metadata required by an SPI operation.
- [ ] Permit local/direct, deterministic test/mock and external provider placement without duplicating State/Guard/Operation/Result semantics.
- [ ] Ensure Workflow projects/reuses StateMachine API/SPI rather than an independent interface model.
- [ ] Preserve identity/type metadata for future assemble `SPI -> API` binding and caller-side API projection.

## WFL-62-03: ActionExecution and Durable Continuation ABI

Stage Status:
- Current status: OPEN
- Owner: Cozy StateMachine / generator owner
- Update rule: Mark DONE only after generated ABI represents internal completion, external suspension, failure and typed resume without mode semantics.

- [ ] Define the closed `ActionExecution = Completed | Suspended | Failed` StateMachine-general ABI.
- [ ] Define typed Completed(Result), Failed(Error) and Suspended(Continuation) payload semantics.
- [ ] Define Continuation, ContinuationResult, ContextBundle, ContextReference, ContextSnapshot, CompletionContract and EvidenceContract.
- [ ] Require instance/run identity, expected revision, minimum context, completion/evidence and typed resume result.
- [ ] Generate fail-closed stale-result semantics through ContextSnapshot.
- [ ] Exclude Workflow-wide Orchestration/Continuation mode and semantic InvocationBinding from canonical ABI.
