# Phase 62.1: StateMachine API/SPI and Durable ActionExecution ABI

status=planned
split_full_test_policy=final-only
split_full_validation_method=sbt-full-suite
split_validation_bootstrap=none
validation_ownership=aggregate-deferred
aggregate_validation_owner=PHASE-62.3
aggregate_validation_sequence=["PHASE-62","PHASE-62.1","PHASE-62.2","PHASE-62.3"]
depends_on=phase-62.md

Status: planned
Planned at: 2026-09-17
Development item: DEV-030 (split child)
Split from Phase 62: 2026-09-17
Predecessor: [Phase 62](phase-62.md)
Successor: [Phase 62.2](phase-62.2.md)

## Phase Plan Gate

Phase Plan Gate: PROCEED

- target: calibrated expected duration centered on 6h; allowed ceiling 8h
- estimate_calibration: no comparable completed Workflow producer Phase; this isolates the 7.0-8.0h generic public ABI interval.
- planning_demand: protected-decision
- recommended_parent_profile: gpt-5.6-terra / xhigh
- profile_cost_role: expensive reasoning kernel
- expensive_reasoning_kernel: establish one generic StateMachine Provided API / Required SPI and `Completed | Suspended(Continuation) | Failed` ABI, including fail-closed stale results.
- frozen_profile_transition_handoff: committed source/lowering authority from Phase 62; this Phase produces the API/SPI and continuation schema authority for Phase 62.2.
- parent_reasoning_mode_policy: standard
- estimated_at_recommended_profile: 7.0-8.0h; within ceiling.
- incoming_semantic_handoffs: [{ from_child: PHASE-62, to_child: PHASE-62.1, kind: authority, input: accepted Workflow source/lowering semantics, action: consume normalized StateMachine model, output: admissible generic ABI, owner: Cozy PHASE-62, invalidation_reason: changed Workflow identity, normalization, progression boundary or source correlation semantics }]
- merge_attempts_for_every_sub_4h_child: none
- rebalance_attempts_for_every_sub_5h_child: none
- adjacent_merge_structural_rejection_evidence: merging with Phase 62 restores source-plus-public-ABI scope above ceiling; merging with Phase 62.2 restores ABI-plus-generator scope above ceiling.
- profile_cost_only_rejection_forbidden: true
- short_child_basis: none
- overhead_tradeoff: committed ABI permits lower-cost generator and fixture work without reopening public contract decisions.
- agent_reasoning_mode_policy: default standard; consider pro only at an eligible launch with frozen quality-first evidence.
- runtime_suitability: re-evaluate in the Phase execution task.
- source: applied split from Phase 62 on 2026-09-17.

## Purpose and boundary

Consume Phase 62's normalized source semantics and define reusable StateMachine API/SPI:
stable operation identity, typed input/result, Context/Completion/Evidence/capability metadata,
provider binding, closed ActionExecution and durable continuation contracts.

`Suspended(Continuation)` is an Action provider result, not a Workflow-wide mode or
`InvocationBinding` property. Participant/capability metadata may describe a provider but
never selects whether execution suspends.

## Work stack

| ID | Outcome | Status |
| --- | --- | --- |
| WFL-62-02 | One reusable StateMachine Provided API / Required SPI contract. | OPEN |
| WFL-62-03 | Closed ActionExecution and durable Continuation ABI. | OPEN |

## Acceptance and exclusions

Acceptance fixes generic API/SPI schema, provider binding, typed completion, Continuation,
ContinuationResult, ContextBundle, ContextReference, ContextSnapshot, CompletionContract and
EvidenceContract, including stale-result rejection. Phase 62.2 consumes the frozen schema.

No concrete provider, Skill host, UI, REST, model selection, WorkflowRun datastore or
Workflow-specific parallel API/SPI model is implemented here.

## References

- [Phase 62 source predecessor](phase-62.md)
- [Phase 62.1 Checklist](phase-62.1-checklist.md)
- [Phase 62.2 generator successor](phase-62.2.md)
- [Current StateMachine API/SPI clarification](phase-62-statemachine-api-spi-clarification.md)
