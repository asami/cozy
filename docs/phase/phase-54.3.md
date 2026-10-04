# Phase 54.3: Dynamic Workflow and StateMachine Metadata

Status: CLOSED

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
It consumes the frozen cozy.cml.semantic-metadata.v1 foundation/handoff and
the completed static
and Classification vocabularies. It produces the dynamic cross-reference
handoff consumed by the Use Case and final consumer-fixture child.

Phase Plan Gate: PROCEED

- target: approximate-six-hour packing target; preferred 4–8 h band
- planning_demand: bounded-settled
- recommended_parent_profile: gpt-5.6-terra / high
- profile_cost_role: lower-cost execution
- expensive_reasoning_kernel: none
- frozen_profile_transition_handoff: Phase 54 cozy.cml.semantic-metadata.v1
  foundation/handoff;
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
| MMD-543-01 | Publish Workflow identity, purpose, activities, control flow, branch/merge, participants, affected domain elements, and declared related operations/events/state effects. | CLOSED |
| MMD-543-02 | Publish StateMachine, state, and transition identities with declared triggers, guards, actions, and owning/affected domain elements. | CLOSED |
| MMD-543-03 | Preserve stable declared links among Workflow activities, StateMachine transitions, operations, events, and rules, including admitted operation/event cause and reaction links. | CLOSED |
| MMD-543-04 | Freeze dynamic fixtures and the Phase 54.4 cross-view handoff without defining runtime enforcement. | CLOSED |

## Current implementation boundary

S54.3.1 / DYNAMIC-PRODUCER is CLOSED under
P543-S5431-IMPLEMENTATION-001 revision 1. The complete additive
`cozy.cml.dynamic.v1` supplier, five cohesive modules and DM-01–09 executable
specification are authored. The [specification](../spec/cml-dynamic-metadata.md)
and [design](../design/cml-dynamic-metadata.md) freeze exact qualified identities,
full-record multiset binding, explicit gaps, strict child admission and literal
declared links. Seven focused/predecessor suites passed all 122 tests. The
independent protected Step review accepted DM-01–09 behavior and identified one
private-helper naming defect. The exact sixteen-name repair and direct calls
passed `Test/compile`; parent M0 amendment resolved that defect in the original
Step review chain. The parent manual native Step commit is
`feb4ba862635a56fa0519ae75cef0bdd910739b2`. This acceptance covers the supplier only.

S54.3.2 / DYNAMIC-NAVIGATION is CLOSED under
P543-S5432-IMPLEMENTATION-001 revision 1. CmlDynamicTopology retains the original
Graph, exact fifteen node kinds, four literal link kinds, occurrence-aware
Catalog ordering, Local-only partial endpoint indexes and seven exact projection
lookups. DT-01–08 author complete independent facts, exact diagnostic cases,
supplier/predecessor round trips and an active property requesting at least
100 successes and zero discards. All 132 tests passed across eight selected
focused/predecessor suites, with SBT/wrapper exit 0 and the shared lock released.
Independent lightweight Step review P543-S5432-REVIEW-001 revision 1 accepted
the complete seven-path accumulator with zero current blockers. The parent
records the manual local Step commit in its native audit:
`4a57f37244be8f0bb5238bb92bb951ca615c8930`.

S54.3.3 / DYNAMIC-FIXTURES-HANDOFF is CLOSED under
P543-S5433-IMPLEMENTATION-001 revision 1. Two fixed UTF-8 resources retain the
accepted Classification prefix and add exact declared/absence facts: 42 records,
three Terms and seventeen carriers, with 23 nodes and eight literal links.
DF-01–09 author independent expected data, full wire/source/absence/query facts,
safe negative diagnostics and an active 100-success/zero-discard property over
both modes, Catalog/carrier permutations and object-key reconstruction.
The [handoff](../design/cml-dynamic-metadata-handoff.md) records exact internal
supplier/reader/navigation APIs, synthetic caller provenance and the runnable
nine-suite recipe. All 141 tests passed in nine selected focused/predecessor
suites; P543-S5433-REVIEW-001 revision 1 independently accepted DF-01–09 with zero
current blockers. The parent manual native Step commit is
`2ef722dda75eaadf07b224b4e9548fc367025b06`; its audit is verified.
Phase 54.4 remains unstarted.

## Final acceptance and release

S54.3.1, S54.3.2 and S54.3.3 are CLOSED at native parent manual commits
`feb4ba862635a56fa0519ae75cef0bdd910739b2`,
`4a57f37244be8f0bb5238bb92bb951ca615c8930` and
`2ef722dda75eaadf07b224b4e9548fc367025b06`.
The sole independent full Phase review P543-FULL-REVIEW-001 revision 1 is PASS
with zero current blockers. Original typed review history remains in
P543-REVIEW-LEDGER-001 revision 6; Phase repair cycles consumed: zero.
Ordinary final `sbt --batch test` passed 2769 tests across 192 suites; failures and aborted suites were zero. The 9 pre-existing canceled cases retain their documented optional/deferred scope. SBT/wrapper exit 0 and `lock=released` were verified under P543-FINAL-FULL-VALIDATION-ADMISSION-001 revision 1.
The distinct parent manual local release binds P543-CLOSURE-001 revision 1;
its actual Git revision and committed-tree/clean-tree audit are recorded after
successful commit in the private closure receipt. This committed closure
projection becomes authoritative only on that verified release.
HYG-P543-S5431-001 is persisted once in its
[canonical follow-up](../journal/2026/10/2026-10-04-phase-54.3-hygiene-follow-up.md);
it remains separate legacy projector maintenance. No Development Candidate
exists and no empty candidate journal is created. Phase 54.4 remains PLANNED
and unstarted; runtime and external consumer adoption retain their owners.

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
- [CML Semantic Foundation Handoff](../design/cml-semantic-foundation-handoff.md)
- [Phase 54.3 checklist](phase-54.3-checklist.md)
- [Phase 54.4](phase-54.4.md)
- [Component Dashboard model metadata note](../notes/component-dashboard-model-metadata.md)
