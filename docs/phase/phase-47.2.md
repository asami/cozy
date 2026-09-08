# Phase 47.2 - UnitOfWork Planning and Deterministic Test Foundation

Status: completed; release validation and distinct release-commit evidence pending
Planned at: 2026-09-05
Revised at: 2026-09-08
Depends on: Phase 47.1
Successor: [Phase 47.2.1](phase-47.2.1.md)
Cross-repository consumer work: `asami/goldenport-cncf` Phase 64.2

## Purpose

Establish the consumer-side execution inventory, `UnitOfWorkOp` effect
classification, planner model, and deterministic test-runtime foundation that
will consume a later Cozy CML logical-action compiler result. This retained
first delivery unit preserves the original Phase 47.2 identity.

`UnitOfWorkOp[A]` remains CNCF's canonical executable-intent algebra and
`ExecProgram[A] = Program[UnitOfWorkOp, A]` remains its canonical program
shape. This Phase records and proves the consumer foundation; it does not yet
implement a CML compiler or compile an Order/Payment/Shipment fixture.

## Split note — 2026-09-08

The user approved the ordered sequence `47.2 -> 47.2.1 -> 47.2.2` through
the explicit `$cncf-split-phase Phase 47.2` request, with the final child
identity corrected to `47.2.2`. The pre-split estimate was 19–24 hours, so it
materially exceeded the preferred 4–8 hour packing band. The split partitions
the previously planned ACP and UTP work exactly once:

| Phase | Closure result | Estimate |
| --- | --- | --- |
| 47.2 | Consumer inventory, effect classification, planner contract, and deterministic test foundation | 6–8 h |
| 47.2.1 | Cozy logical-action boundary, resolver/compiler, generated ABI, and static validation | 6–7 h |
| 47.2.2 | Cross-level composition and Order/Payment/Shipment execution acceptance | 7–8 h |

No completed Phase 47.2 work existed to move. The added Phase, handoff,
validation, review, and commit overhead is accepted because each child now has
one independently closable acceptance boundary. No expensive reasoning kernel
remains open: the existing `UnitOfWorkOp` authority and the no-parallel-action
algebra decision are settled. The user-selected `gpt-5.6-terra / xhigh` remains
the least-cost compatible parent profile for the protected cross-repository
contracts; there is no profile-transition handoff.

### Pre-split gate evidence

The former Phase Plan Gate reported `SPLIT_REQUIRED` for the combined 19–24 h
scope. That is dated pre-split evidence only and is not the current gate of
this retained child.

### Current structural gate

Phase Plan Gate: PROCEED

- target: approximate-six-hour packing target; preferred 4–8 h band
- planning_demand: bounded-settled
- recommended_parent_profile: gpt-5.6-terra / xhigh
- profile_cost_role: lower-cost execution
- expensive_reasoning_kernel: none
- frozen_profile_transition_handoff: none
- parent_reasoning_mode_policy: standard
- estimated_at_recommended_profile: 6–8 h; within preferred band
- merge_attempts_for_every_sub_4h_child: none
- adjacent_merge_structural_rejection_evidence: none
- profile_cost_only_rejection_forbidden: true
- short_child_exception: none
- overhead_tradeoff: two added Phase handoffs replace an over-band combined
  closure; the resulting independently reviewable contracts outweigh that
  overhead
- agent_reasoning_mode_policy: default standard; consider pro only at an
  eligible agent launch when frozen quality-first evidence justifies it
- runtime_suitability: re-evaluate in the Phase execution task
- source: approved split from Phase 47.2

## In-scope work

The work ledger for this child is:

| ID | Consumer-owned outcome | Status |
| --- | --- | --- |
| ACP-01 | Cross-repository execution inventory records the present CML action/effect surface and the CNCF `UnitOfWorkOp`, `ExecProgram`, Free/UoW DSL, interpreter, metadata, and test boundaries. | done |
| UTP-01 | `asami/goldenport-cncf` inventories `UnitOfWorkOp`, `ExecProgram`, `ExecUowM`, direct/declarative DSLs, interpreter/drivers, metadata, and existing tests. | done |
| UTP-03 | Existing `UnitOfWorkOp` cases are classified for local, 2PC, after-commit, compensatable, and irreversible planning where relevant. | done |
| UTP-04 | The consumer freezes explicit segment planning, ordering, capability admission, idempotency, and compensation planning. | done |
| UTP-05 | The consumer defines/implements program inspection, fake drivers, typed result stubbing, and deterministic failure injection. | done |

`ACP-01` is the sole Cozy item in this child. `UTP-01`, `UTP-03`, `UTP-04`, and
`UTP-05` remain CNCF-owned work in Phase 64.2; this Phase owns only their
cross-repository sequencing and the evidence boundary. No action resolver,
generated ABI, Action compilation, or fixture acceptance is duplicated here.

## Closure criteria

- The inventory identifies the present producer and consumer seams without
  asserting that CML is already executable through them.
- `UnitOfWorkOp` remains the sole canonical executable-intent algebra; no
  StateMachine/Workflow `ActionOp` family is introduced.
- The committed CNCF foundation classifies all 51 existing `UnitOfWorkOp`
  constructors, preserves ordered occurrence and external-boundary planning,
  and provides deterministic typed recording and failure injection without
  production I/O.
- The final Step accumulator test passed 9 specifications in 2 suites with no
  failures, and the independent full Phase review `P472-PHASE-FULL-REVIEW-001`
  sealed no Current Phase Blocker, Hygiene, or Development Candidate.
- The frozen consumer contract is sufficient for Phase 47.2.1 to bind CML
  logical actions directly to `ExecProgram`; Phase 47.2.1 and Phase 47.2.2
  remain planned and unstarted.
- The matching checklist records the completed child boundary. Final full
  validation and the distinct release commit are the remaining mechanical
  closure gate; neither is claimed by this document alone.

## Non-goals

- Defining CML action identity or a CML resolver/compiler.
- Generating a Cozy Action binding/program ABI.
- Executing a StateMachine/Workflow transition or the shared fixture.
- Replacing or duplicating `UnitOfWorkOp`.
- Introducing provider I/O as the default test path.

## References

- [Phase 47.2 checklist](phase-47.2-checklist.md)
- [Phase 47.2.1](phase-47.2.1.md)
- `asami/goldenport-cncf/docs/phase/phase-64.2.md`
