# Phase 54.1: Faithful Structure Metadata

Status: IN_PROGRESS

Plan date: 2026-09-09
Split from: [Phase 54](phase-54.md)
Depends on: Phase 54
Successor: [Phase 54.2](phase-54.2.md)

## Purpose

Extend the frozen Phase 54 cozy.cml.semantic-metadata.v1 foundation and
handoff with faithful static
Structure metadata. Preserve Entity, Value, Aggregate, composition,
aggregation, and association as distinct constructs, including declared
relation semantics, without requiring a SimpleModeling.org editing-oriented
consumer or Textus CBD Support to parse CML source.

## Provenance and structural gate

This is the second sequential child of the 2026-09-09 approved Phase 54 split.
It consumes the Phase 54 stable-identity and publication-contract handoff and
produces the frozen Structure metadata vocabulary and fixtures consumed by the
later Classification, dynamic, and cross-view acceptance children.

Phase Plan Gate: PROCEED

- target: approximate-six-hour packing target; preferred 4–8 h band
- planning_demand: bounded-settled
- recommended_parent_profile: gpt-5.6-terra / high
- profile_cost_role: lower-cost execution
- expensive_reasoning_kernel: none
- frozen_profile_transition_handoff: Phase 54 cozy.cml.semantic-metadata.v1
  foundation/handoff: identity, provenance, absence, compatibility, and
  publication contract
- parent_reasoning_mode_policy: standard
- estimated_at_recommended_profile: 6–8 h; within preferred band
- merge_attempts_for_every_sub_4h_child: none
- adjacent_merge_structural_rejection_evidence: none
- profile_cost_only_rejection_forbidden: true
- short_child_exception: none
- overhead_tradeoff: the explicit Structure handoff keeps relation semantics
  reviewable without reopening the identity/publication decision
- agent_reasoning_mode_policy: default standard; consider pro only at an
  eligible agent launch when frozen quality-first evidence justifies it
- runtime_suitability: re-evaluate in the Phase execution task
- source: approved split from Phase 54

## In-scope work

| ID | Outcome | Status |
| --- | --- | --- |
| MMD-541-01 | Publish distinct Entity, Value, Aggregate, composition, aggregation, and association identities and kinds. Slice `MMD-541-01A` passed focused validation and independent protected focused Step review; see checklist evidence. | ACCEPTED |
| MMD-541-02 | Preserve declared endpoint roles, cardinality, navigability, ownership, independent existence, create/delete, reassignment/reparenting, lifecycle propagation, and aggregate-boundary semantics. | PLANNED |
| MMD-541-03 | Prove composition and aggregation are not flattened into generic association, and preserve explicit absence where CML does not declare a policy. | PLANNED |
| MMD-541-04 | Freeze Structure fixtures and the later-child handoff on the cozy.cml.semantic-metadata.v1 identity/publication surface. | PLANNED |

## Closure criteria

- Every supported static construct and relation is published distinctly on the
  Phase 54 identity/publication foundation.
- Declared lifecycle and ownership semantics are preserved faithfully; absent
  source semantics are not synthesized.
- Fixtures prove that composition and aggregation remain distinguishable from
  association without source parsing.
- The Structure handoff is frozen for Phases 54.2 through 54.4; no
  SimpleModeling.org site edit, Dashboard rendering, or CNCF enforcement is
  claimed.

## Non-goals

- Changing the Phase 54 cozy.cml.semantic-metadata.v1 identity, provenance, or publication-contract decision.
- Classification, Workflow, StateMachine, or Use Case metadata.
- SimpleModeling.org integration, site mutation, publication, deployment,
  upload, or push.

## References

- [Phase 54](phase-54.md)
- [CML Semantic Foundation Handoff](../design/cml-semantic-foundation-handoff.md)
- [Phase 54.1 checklist](phase-54.1-checklist.md)
- [Phase 54.2](phase-54.2.md)
- [Component Dashboard model metadata note](../notes/component-dashboard-model-metadata.md)
