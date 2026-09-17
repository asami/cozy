# Phase 62.3 Checklist: Skill-Driven Workflow Producer Fixture and CNCF Handoff

Phase status: planned
Updated: 2026-09-17
Ledger for: [Phase 62.3](phase-62.3.md)
Predecessor: [Phase 62.2](phase-62.2.md)
Repository-full validation: aggregate final owner for `PHASE-62` -> `PHASE-62.1` -> `PHASE-62.2` -> `PHASE-62.3`

This is the sole completion ledger for WFL-62-05 and WFL-62-06. It consumes generated ABI
and performs the serial chain's aggregate full SBT validation at release.

## WFL-62-05: Skill-Driven Producer Fixture

Stage Status:
- Current status: OPEN
- Owner: Cozy Phase 62.3 coordinating with CNCF Phase 77 / `sm-workflow`
- Update rule: Mark DONE only after a real producer fixture demonstrates internal completion plus one external SPI suspension/resume boundary.

- [ ] Add a real CML Workflow fixture equivalent to `BuildProject -> RunTests -> ReviewChange -> CommitChanges`.
- [ ] Model Build/Test/Commit as internal Actions/providers that produce Completed.
- [ ] Model ReviewChange as a Required SPI operation capable of Suspended(Continuation).
- [ ] Verify typed ReviewResult resumes the suspended Action and permits subsequent transition/closing action.
- [ ] Verify deterministic test-provider binding exercises the same StateMachine semantics without an AI/Skill provider.
- [ ] Verify stale ReviewResult is rejected by generated snapshot/revision contract.

## WFL-62-06: CNCF Handoff and Closure

Stage Status:
- Current status: OPEN
- Owner: Cozy Phase 62.3 coordinating with CNCF Phase 77
- Update rule: Mark DONE only after producer evidence and consumer handoff are frozen; CNCF runtime acceptance remains external.

- [ ] Record exact CML source fixture and generated artifact digests/revision.
- [ ] Freeze StateMachine/Workflow ABI version and compatibility requirements.
- [ ] Document Provided API / Required SPI and ActionExecution/Continuation contracts for CNCF admission.
- [ ] Record future assemble/API projection compatibility metadata without claiming implementation.
- [ ] Complete Cozy generation tests, executable specifications, regression validation, independent review, clean re-review where required, and aggregate final-owner release closure.
- [ ] Do not claim CNCF runtime, Generic Skill Workflow Support, `sm-workflow` SQLite/CLI, Workflow-to-Workflow REST, UI Workflow or Flutter completion.
