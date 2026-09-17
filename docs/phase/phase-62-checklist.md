# Phase 62 Checklist: CML WORKFLOW Source and StateMachine Lowering

Phase status: closed
Updated: 2026-09-17
Ledger for: [Phase 62](phase-62.md)
Successor ledger: [Phase 62.1](phase-62.1-checklist.md)
Repository-full validation: aggregate deferred to Phase 62.3

This is the sole completion ledger for WFL-62-01. WFL-62-02 through WFL-62-06 moved
exactly once to successor ledgers; no successor item is complete.

## WFL-62-01: Workflow Source Contract and Lowering

Stage Status:

- Current status: CLOSED
- Owner: Cozy CML / SimpleModeler owner
- Update rule: Mark DONE only after real `WORKFLOW` source fixes identity, versioning,
  StateMachine/Composite StateMachine normalization, and explicit progression semantics.

- [x] Define `WORKFLOW` as a first-class CML declaration that normalizes to the existing
      StateMachine / Composite StateMachine semantic model.
- [x] Define Workflow identity, version, constituent/reference boundaries, and
      declared-definition versus runtime-instance separation.
- [x] Preserve existing StateMachine and Composite StateMachine source compatibility.
- [x] Prohibit CML from treating entity persistence as the WorkflowInstance store.
- [x] Define automatic-transition guards/effects and external semantic SPI boundaries
      without Action-name or consumer-default inference.
- [x] Reject ambiguous progression, undeclared required operations, raw execution
      surfaces, and unsupported Workflow-only control semantics.

Closure evidence: focused SBT receipt
`55b5dfc8cc24539921e3a1b4a311333649ec948d7f471c775769fccccd5dac05` and
clean focused re-review `PHASE-62-WFL-62-01-FOCUSED-REREVIEW-003` close this
ledger. Repository-full validation remains aggregate-deferred to Phase 62.3.

## Split transfer record

WFL-62-02/03 move to [Phase 62.1](phase-62.1-checklist.md), WFL-62-04 to
[Phase 62.2](phase-62.2-checklist.md), and WFL-62-05/06 to
[Phase 62.3](phase-62.3-checklist.md). They consume this Phase's committed source/lowering
handoff and are not Phase 62 checklist items.
