# Phase 47.2.1 - CML Logical-Action Compiler and Generated ABI

Status: DONE
Planned at: 2026-09-08
Closed at: 2026-09-08 (effective with the Phase 47.2.1 final release commit)
Split from: [Phase 47.2](phase-47.2.md)
Depends on: Phase 47.2
Successor: [Phase 47.2.2](phase-47.2.2.md)
Cross-repository consumer work: `asami/goldenport-cncf` Phase 64.2

## Purpose

Define and implement the Cozy producer contract that resolves CML logical
actions deterministically into CNCF's existing `ExecProgram[UnitOfWorkOp, A]`.
The result preserves typed identity, provenance, transaction/reversibility,
compensation, idempotency, ordering, and ABI-version semantics without
introducing a second runtime algebra.

This child consumes the frozen Phase 47.2 inventory/classification/planner/test
foundation and produces the producer ABI and static-validation handoff consumed
by Phase 47.2.2 and CNCF UTP-02.

## Provenance and structural gate

The 2026-09-08 approved split retains Phase 47.2 as the first unit and creates
this second sequential child. It consumes the Phase 47.2 consumer-foundation
handoff and produces the stable compiler/ABI handoff for Phase 47.2.2.

Phase Plan Gate: PROCEED

- target: approximate-six-hour packing target; preferred 4–8 h band
- planning_demand: bounded-settled
- recommended_parent_profile: gpt-5.6-terra / xhigh
- profile_cost_role: lower-cost execution
- expensive_reasoning_kernel: none
- frozen_profile_transition_handoff: Phase 47.2 consumer inventory,
  classification, planner, and deterministic-test contract
- parent_reasoning_mode_policy: standard
- estimated_at_recommended_profile: 6–7 h; within preferred band
- merge_attempts_for_every_sub_4h_child: none
- adjacent_merge_structural_rejection_evidence: none
- profile_cost_only_rejection_forbidden: true
- short_child_exception: none
- overhead_tradeoff: one explicit compiler/ABI handoff permits the final
  acceptance child to test a frozen producer surface rather than reopen it
- agent_reasoning_mode_policy: default standard; consider pro only at an
  eligible agent launch when frozen quality-first evidence justifies it
- runtime_suitability: re-evaluate in the Phase execution task
- source: approved split from Phase 47.2

## In-scope work

| ID | Outcome | Status |
| --- | --- | --- |
| ACP-02 | Freeze CML logical-action identity, typing, metadata, compensation, idempotency, and binding semantics without a second runtime algebra. | DONE |
| ACP-03 | Resolve/compile CML logical actions deterministically to `ExecProgram[UnitOfWorkOp, A]`. | DONE |
| ACP-06 | Emit stable SimpleModeler action binding/program metadata compatible with CNCF UnitOfWork execution. | DONE |
| ACP-07 | Implement tractable binding, compensation, ordering, idempotency, type, and Free/UoW-path validation. | DONE |
| UTP-02 | CNCF Phase 64.2 admits and versions the received CML compilation ABI. | external pending |

`UTP-02` remains a CNCF-owned consumer admission item. The generated producer
contract and its versioned handoff are the only cross-repository responsibility
of this child.

## Closure criteria

- Every CML logical action has a typed resolver/compiler path to existing
  `ExecProgram[UnitOfWorkOp, A]`, or is statically rejected.
- Generated metadata preserves the required identity, provenance, ordering,
  transaction, reversibility, compensation, idempotency, source, and version
  meaning without provider handles, scripts, or opaque callbacks.
- No StateMachine/Workflow-specific `ActionOp` hierarchy is added.
- Static validation rejects missing/invalid bindings and semantic conflicts at
  the producer boundary.
- The exact ABI/version is recorded as a Cozy producer handoff for CNCF UTP-02;
  CNCF admission remains pending, is not a Cozy closure condition, and is not
  claimed by this child.
- The matching checklist, focused validation/review, and this release closure
  are complete. Phase 47.2.2 remains a separately planned successor.

## Non-goals

- Reworking the settled UnitOfWork planner or test-interpreter foundation.
- Proving production execution or cross-level fixture behavior.
- Adding a provider-specific transaction API to CML.
- Creating a new action runtime algebra.

## References

- [Phase 47.2](phase-47.2.md)
- [Phase 47.2.1 checklist](phase-47.2.1-checklist.md)
- [Phase 47.2.2](phase-47.2.2.md)
- `asami/goldenport-cncf/docs/phase/phase-64.2.md`
