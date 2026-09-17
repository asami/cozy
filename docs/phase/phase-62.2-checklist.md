# Phase 62.2 Checklist: Generated StateMachine/Workflow ABI and Bootstrap

Phase status: planned
Updated: 2026-09-17
Ledger for: [Phase 62.2](phase-62.2.md)
Predecessor: [Phase 62.1](phase-62.1.md)
Successor ledger: [Phase 62.3](phase-62.3-checklist.md)
Repository-full validation: aggregate deferred to Phase 62.3

This is the sole completion ledger for WFL-62-04. It consumes frozen Phase 62.1 ABI and
produces generated producer artifacts for Phase 62.3.

## WFL-62-04: Generated ABI and Bootstrap

Stage Status:
- Current status: OPEN
- Owner: Cozy generator / generated-contract owner
- Update rule: Mark DONE only after deterministic generated artifacts can be admitted by CNCF without CML reparsing.

- [ ] Generate StateMachine/Workflow identity, API/SPI, ActionExecution and Continuation/Context/Completion/Evidence schemas.
- [ ] Preserve source correlation/provenance for State, Action, Operation and required SPI identity.
- [ ] Emit direct ComponentFactory bootstrap metadata without runtime policy or inferred name matching.
- [ ] Keep generation deterministic and ABI-versioned.
- [ ] Exclude dialog/open-screen commands, concrete transport, REST URL, specific AI model/provider, raw shell and runtime persistence details.
- [ ] Retain stable metadata for future local/REST API proxy projection without implementing projections.
