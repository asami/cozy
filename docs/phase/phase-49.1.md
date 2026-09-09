# Phase 49.1: Document Project Presentation Semantics Workflow Surfaces

Status: IN PROGRESS

Plan date: 2026-09-09
Split from: [Phase 49](phase-49.md)
Depends on: Phase 49
Successor: [Phase 49.2](phase-49.2.md)

## Goal

Expose the accepted Phase-49 presentation-semantics Work Product/state contract
through the normal read-only Document Project surfaces. Strict `verify`,
`inspect`, `plan`, and Dashboard behavior must make readiness, diagnostics,
currentness, dependency blocking, and next authoring action observable without
creating semantic content or confirmation artifacts.

## Provenance and structural gate

This is the second sequential child of the approved 2026-09-09 Phase 49 split.
It consumes the accepted Phase-49 Work Product, state/dependency, and public
operation grammar contract and produces the accepted read-only workflow-surface
behavior that Phase 49.2 consumes.

Phase Plan Gate: PROCEED

- target: approximate-six-hour packing target; preferred 4–8 h band
- planning_demand: bounded-settled
- recommended_parent_profile: gpt-5.6-terra / high
- profile_cost_role: lower-cost execution
- expensive_reasoning_kernel: none
- frozen_profile_transition_handoff: accepted Phase-49 presentation-semantics
  Work Product/state/dependency and public-operation grammar contract
- parent_reasoning_mode_policy: standard
- estimated_at_recommended_profile: 6–8 h; within preferred band
- merge_attempts_for_every_sub_4h_child: none
- adjacent_merge_structural_rejection_evidence: none
- profile_cost_only_rejection_forbidden: true
- short_child_exception: none
- overhead_tradeoff: the separately reviewable read-only surface preserves
  diagnostics and dependency guidance without reopening state or command design
- agent_reasoning_mode_policy: default standard; consider pro only at an
  eligible agent launch when frozen quality-first evidence justifies it
- runtime_suitability: re-evaluate in the Phase execution task
- source: approved split from Phase 49

## In-scope work

| ID | Outcome | Status |
| --- | --- | --- |
| P491-01 | Route strict `verify` through the closed Phase-46 loader/validator and prove valid semantics admit to the Phase-46.1 projection boundary while preserving `DP-SEM-*` diagnostics. Cozy, rather than Document Project, provides the fixed versioned `software-explanation@1` and `presentation@1` Catalogs. | completed |
| P491-02 | Expose schema identity, Core binding/currentness, semantic state, Story Step/Transition and Explanation Structure counts, projection availability, and coverage state through `inspect`; preserve unavailable values as unavailable. | completed |
| P491-03 | Place Presentation Semantics after Content Core and before dependent semantic work in `plan`, and block missing, authoring-incomplete, invalid, or stale work deterministically without execution. | completed |
| P491-04 | Present the same authority as a normal Dashboard stage, with an exact first-blocker authoring action and no Dashboard-generated semantic content. | completed |

## Closure criteria

- Strict validation preserves the closed `DP-SEM-*` failure taxonomy and never
  adds a permissive alias or scaffold-only path.
- `inspect`, `plan`, and Dashboard expose the frozen states and deterministic
  block/action guidance without placeholder success or mutation.
- Focused executable specifications, review, full Cozy validation, and release
  closure prove this read-only surface only.
- Public confirmation routing, stale propagation, Article-9-shaped acceptance,
  publication, deployment, upload, push, and external mutation remain outside
  this child.

## Non-goals

- Reopening the Phase-49 Work Product/state or public-operation grammar.
- Emitting a confirmation HTML or receipt, or adapting the Phase-46.1 renderer.
- Automatic semantic authoring, currentness write-back, or Article 9 editorial
  completion.
- Publication, deployment, registration, upload, push, or external mutation.

## Step completion evidence

- `P491-STEP-REPAIR-VAL-001`: focused `testOnly
  cozy.document.CozyDocumentProjectSpec` passed 93 specifications with 0
  failures after the bounded Step repair.
- The Step review blockers were repaired and the focused closure review was
  accepted through the M0 prose-only repair tail. The final reviewed boundary
  has no Current Step Blocker.
- `CozyExplanation` and `CozyVisualPage` are the sole fixed Catalog providers;
  Document Project consumes their fixed identities and never selects, discovers,
  loads, or falls back among Catalogs.
- Phase-wide full validation, independent Phase review, and release closure
  remain pending.

## References

- [Phase 49](phase-49.md)
- [Phase 49.1 checklist](phase-49.1-checklist.md)
- [Phase 49.2](phase-49.2.md)
- `docs/spec/document-project-presentation-semantics.md`
- `docs/phase/phase-46.md`
- `docs/phase/phase-46.1.md`
