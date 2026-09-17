# Phase 62.3: Skill-Driven Workflow Producer Fixture and CNCF Handoff

status=planned
split_full_test_policy=final-only
split_full_validation_method=sbt-full-suite
split_validation_bootstrap=none
validation_ownership=aggregate-final-owner
aggregate_validation_owner=PHASE-62.3
aggregate_validation_sequence=["PHASE-62","PHASE-62.1","PHASE-62.2","PHASE-62.3"]
depends_on=phase-62.2.md

Status: planned
Planned at: 2026-09-17
Development item: DEV-030 (split child)
Split from Phase 62: 2026-09-17
Predecessor: [Phase 62.2](phase-62.2.md)

## Phase Plan Gate

Phase Plan Gate: PROCEED

- target: calibrated expected duration centered on 6h; allowed ceiling 8h
- estimate_calibration: no comparable Workflow producer completion; this is a 6.0-7.0h fixture/evidence interval after generated ABI projection.
- planning_demand: bounded-settled
- recommended_parent_profile: gpt-5.6-terra / high
- profile_cost_role: lower-cost execution
- expensive_reasoning_kernel: none
- frozen_profile_transition_handoff: committed generated ABI version, schema identities, correlation/provenance and ComponentFactory bootstrap metadata from Phase 62.2.
- parent_reasoning_mode_policy: standard
- estimated_at_recommended_profile: 6.0-7.0h; centered near target and within ceiling.
- incoming_semantic_handoffs: [{ from_child: PHASE-62.2, to_child: PHASE-62.3, kind: authority, input: accepted generated ABI and bootstrap metadata, action: consume deterministic producer shape, output: fixture evidence and CNCF admission handoff, owner: Cozy PHASE-62.2, invalidation_reason: changed ABI version, schema identity, source correlation or bootstrap data }]
- merge_attempts_for_every_sub_4h_child: none
- rebalance_attempts_for_every_sub_5h_child: none
- adjacent_merge_structural_rejection_evidence: merging with Phase 62.2 restores an ABI-generator-consumer interval above ceiling and removes the generated-artifact handoff.
- profile_cost_only_rejection_forbidden: true
- short_child_basis: none
- overhead_tradeoff: final owner pays one extra release boundary but performs the sequence's single full SBT validation after fixture evidence freezes.
- agent_reasoning_mode_policy: default standard; consider pro only at an eligible launch with frozen quality-first evidence.
- runtime_suitability: re-evaluate in the Phase execution task.
- source: applied split from Phase 62 on 2026-09-17.

## Purpose and boundary

Consume Phase 62.2's frozen generated ABI and prove the first Skill-driven producer vertical slice:

```text
BuildProject -> Completed
RunTests -> Completed
ReviewChange -> Suspended(Continuation)
ReviewResult -> resume -> transition
CommitChanges -> Completed
Terminal
```

It owns deterministic fixture/generated-evidence admission, stale ReviewResult rejection, the frozen
ABI compatibility record, and Cozy's consumer handoff to CNCF Phase 77 / `sm-workflow`.
It owns the serial chain's one repository-full SBT suite on its frozen release tree.

## Work stack

| ID | Outcome | Status |
| --- | --- | --- |
| WFL-62-05 | Skill-driven producer fixture with internal completion and external suspension/resume. | OPEN |
| WFL-62-06 | Frozen producer evidence and CNCF consumer handoff. | OPEN |

## Acceptance and exclusions

Acceptance proves direct Build/Test/Commit completion, suspendable Review SPI, typed resume,
deterministic test-provider equivalence and fail-closed stale result rejection. The handoff
gives CNCF frozen generated ABI/evidence; it does not claim CNCF runtime acceptance.

No Generic Skill Workflow Support, SQLite-backed runs, `advance`, CLI behavior, Workflow-to-Workflow
proxy/REST, UI Workflow, Flutter, provider/model dispatch or durable runtime storage is implemented.

## Aggregate final validation

Phase 62.3 is `aggregate-final-owner` for
`["PHASE-62","PHASE-62.1","PHASE-62.2","PHASE-62.3"]`. It verifies committed
predecessors and runs one full SBT suite in addition to its focused validation, review and release.

## References

- [Phase 62.2 generator predecessor](phase-62.2.md)
- [Phase 62.3 Checklist](phase-62.3-checklist.md)
- [Current StateMachine API/SPI clarification](phase-62-statemachine-api-spi-clarification.md)
