# Phase 54.3: Dynamic Workflow and StateMachine Metadata

Status: PLANNED

Plan date: 2026-09-09
Split from: [Phase 54](phase-54.md)
Depends on: Phase 54.2
Successor: [Phase 54.4](phase-54.4.md)

## Purpose

Publish faithful dynamic-model metadata for Workflow and StateMachine as one
independently closable cross-reference boundary. A consumer must be able to
navigate declared activities, flow, states, transitions, triggers, guards,
actions, and affected domain elements through stable links without inventing
runtime semantics or parsing CML source.

## Provenance and structural gate

This is the fourth sequential child of the 2026-09-09 approved Phase 54 split.
It consumes the frozen v2 identity/publication handoff and the completed static
and Classification vocabularies. It produces the dynamic cross-reference
handoff consumed by the Use Case and final consumer-fixture child.

Phase Plan Gate: PROCEED

- target: approximate-six-hour packing target; preferred 4–8 h band
- planning_demand: bounded-settled
- recommended_parent_profile: gpt-5.6-terra / high
- profile_cost_role: lower-cost execution
- expensive_reasoning_kernel: none
- frozen_profile_transition_handoff: Phase 54 v2 identity/publication contract;
  Phase 54.1 Structure and Phase 54.2 Classification vocabularies
- parent_reasoning_mode_policy: standard
- estimated_at_recommended_profile: 6–8 h; within preferred band
- merge_attempts_for_every_sub_4h_child: none
- adjacent_merge_structural_rejection_evidence: none
- profile_cost_only_rejection_forbidden: true
- short_child_exception: none
- overhead_tradeoff: grouping Workflow with StateMachine preserves their
  inseparable declared cross-reference acceptance while keeping final Use Case
  consumer traversal independently reviewable
- agent_reasoning_mode_policy: default standard; consider pro only at an
  eligible agent launch when frozen quality-first evidence justifies it
- runtime_suitability: re-evaluate in the Phase execution task
- source: approved split from Phase 54

## In-scope work

| ID | Outcome | Status |
| --- | --- | --- |
| MMD-543-01 | Publish Workflow identity, purpose, activities, control flow, branch/merge, participants, affected domain elements, and declared related operations/events/state effects. | planned |
| MMD-543-02 | Publish StateMachine, state, and transition identities with declared triggers, guards, actions, and owning/affected domain elements. | planned |
| MMD-543-03 | Preserve stable declared links among Workflow activities, StateMachine transitions, operations, events, and rules, including admitted operation/event cause and reaction links. | planned |
| MMD-543-04 | Freeze dynamic fixtures and the Phase 54.4 cross-view handoff without defining runtime enforcement. | planned |

## Closure criteria

- Workflow and StateMachine semantics are published faithfully and are distinct
  from one another.
- Every declared dynamic cross-reference uses the frozen stable identity
  vocabulary; undeclared policy remains explicit absence.
- Admitted operation/event causal and reaction links remain source-grounded;
  later Event Storming traversal must not infer a missing edge.
- Fixtures demonstrate dynamic navigation without source parsing or a claim of
  CNCF lifecycle enforcement.
- The dynamic handoff is frozen for Phase 54.4; Use Case detail and the final
  consumer traversal remain separately planned.

## Non-goals

- Reopening prior identity, static Structure, or Classification decisions.
- Use Case metadata or final consumer fixture acceptance.
- CNCF runtime semantics, Dashboard rendering, or SimpleModeling.org site work.

## References

- [Phase 54.2](phase-54.2.md)
- [Phase 54.3 checklist](phase-54.3-checklist.md)
- [Phase 54.4](phase-54.4.md)
- [Component Dashboard model metadata note](../notes/component-dashboard-model-metadata.md)
