# Phase 62.2: Generated StateMachine/Workflow ABI and Bootstrap

status=planned
split_full_test_policy=final-only
split_full_validation_method=sbt-full-suite
split_validation_bootstrap=none
validation_ownership=aggregate-deferred
aggregate_validation_owner=PHASE-62.3
aggregate_validation_sequence=["PHASE-62","PHASE-62.1","PHASE-62.2","PHASE-62.3"]
depends_on=phase-62.1.md

Status: planned
Planned at: 2026-09-17
Development item: DEV-030 (split child)
Split from Phase 62: 2026-09-17
Predecessor: [Phase 62.1](phase-62.1.md)
Successor: [Phase 62.3](phase-62.3.md)

## Phase Plan Gate

Phase Plan Gate: PROCEED

- target: calibrated expected duration centered on 6h; allowed ceiling 8h
- estimate_calibration: no comparable Workflow producer completion; this is a 5.5-6.5h settled projection interval after the predecessor ABI handoff.
- planning_demand: bounded-settled
- recommended_parent_profile: gpt-5.6-terra / high
- profile_cost_role: lower-cost execution
- expensive_reasoning_kernel: none
- frozen_profile_transition_handoff: committed StateMachine API/SPI, ActionExecution and Continuation schema authority from Phase 62.1.
- parent_reasoning_mode_policy: standard
- estimated_at_recommended_profile: 5.5-6.5h; centered near target and within ceiling.
- incoming_semantic_handoffs: [{ from_child: PHASE-62.1, to_child: PHASE-62.2, kind: authority, input: accepted StateMachine API/SPI and continuation ABI, action: consume frozen schemas without InvocationBinding, output: admissible deterministic generated ABI, owner: Cozy PHASE-62.1, invalidation_reason: changed operation identity, typed payload, provider binding or stale-result contract }]
- merge_attempts_for_every_sub_4h_child: none
- rebalance_attempts_for_every_sub_5h_child: none
- adjacent_merge_structural_rejection_evidence: merging with Phase 62.1 exceeds ABI-plus-generator ceiling; merging with Phase 62.3 combines generator work with required end-to-end consumer evidence.
- profile_cost_only_rejection_forbidden: true
- short_child_basis: none
- overhead_tradeoff: one handoff prevents fixture acceptance from changing the generated ABI and admits Terra/high execution.
- agent_reasoning_mode_policy: default standard; consider pro only at an eligible launch with frozen quality-first evidence.
- runtime_suitability: re-evaluate in the Phase execution task.
- source: applied split from Phase 62 on 2026-09-17.

## Purpose and boundary

Generate deterministic, ABI-versioned StateMachine/Workflow artifacts from the committed
Phase 62.1 schema. This phase owns stable schema identity, source correlation/provenance and
direct ComponentFactory bootstrap metadata only.

It does not add runtime policy, inferred name matching, concrete transport, specific provider/model
selection, raw shell, UI, REST, persistence or caller proxy implementation. Phase 62.3 consumes
the frozen artifact shape for the producer fixture and consumer-facing evidence.

## Work stack

| ID | Outcome | Status |
| --- | --- | --- |
| WFL-62-04 | Deterministic ABI-versioned generated schemas and bootstrap metadata. | OPEN |

## References

- [Phase 62.1 ABI predecessor](phase-62.1.md)
- [Phase 62.2 Checklist](phase-62.2-checklist.md)
- [Phase 62.3 fixture and handoff successor](phase-62.3.md)
