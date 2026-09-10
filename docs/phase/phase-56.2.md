# Phase 56.2: Document Project Executability State and Verification

Status: PLANNED

Plan date: 2026-09-10
Split from: [Phase 56](phase-56.md)
Depends on: Phase 56.1
Predecessor: [Phase 56.1](phase-56.1.md)
Development item: DEV-019

## Goal

Expose one closed, read-only Document Project executability model and its
verification policy. Logical selection, prerequisite readiness, provider
availability, accepted-output currentness, and immediate executability must be
distinct typed states projected consistently through `inspect`, `plan`,
`verify`, and Dashboard. Verification defaults to `structural`; `visual`
verification is explicit and bounded.

## Provenance and structural gate

This final sequential child of the 2026-09-10 approved Phase 56 split consumes
the accepted native execution and evidence/currentness handoffs of Phases 56
and 56.1. It produces the complete native production-execution state and
verification-policy closure required before Phase 57 may consume the sequence.

Phase Plan Gate: PROCEED

- target: approximate-six-hour packing target; preferred 4–8 h band
- planning_demand: bounded-settled
- recommended_parent_profile: gpt-5.6-terra / high
- profile_cost_role: lower-cost execution
- expensive_reasoning_kernel: none
- frozen_profile_transition_handoff: Phase 56 native dispatch/result contract
  and Phase 56.1 accepted-evidence, append-only-attempt, and derived-currentness
  contract
- parent_reasoning_mode_policy: standard
- estimated_at_recommended_profile: 7–8 h; within preferred band
- merge_attempts_for_every_sub_4h_child: none
- adjacent_merge_structural_rejection_evidence: Phase 56 + Phase 56.1 is
  12–15 h, above the <=8 h ceiling; Phase 56.1 + Phase 56.2 is 13–15 h, above
  the same ceiling
- profile_cost_only_rejection_forbidden: true
- short_child_exception: none
- overhead_tradeoff: the final policy/projection closure consumes frozen native
  results and accepted evidence, so its review need not reopen provider
  dispatch or atomic evidence semantics
- agent_reasoning_mode_policy: default standard; consider pro only at an
  eligible agent launch when frozen quality-first evidence justifies it
- runtime_suitability: re-evaluate in the Phase execution task
- source: approved split from Phase 56

## In-scope work

| ID | Outcome | Status |
| --- | --- | --- |
| P562-01 | Define closed typed states for logical selection, prerequisite readiness, provider availability, accepted-output currentness, and immediate executability. | planned |
| P562-02 | Project the same state consistently through `inspect`, `plan`, `verify`, and Dashboard while keeping planning read-only and consuming Phase 49.3 presentation-semantics state. | planned |
| P562-03 | Introduce and propagate the closed `structural | visual` verification policy through provider and review boundaries. | planned |
| P562-04 | Make structural verification the default and visual verification explicitly selected, bounded, and distinct from selected public image Work Products. | planned |

## Closure criteria

- Every native operation exposes exact closed state for selection, prerequisites,
  provider availability, accepted-output currentness, and immediate
  executability through every read-only projection.
- `plan` does not execute or mutate; `run` remains the sole execution boundary.
- `structural` verification validates semantic authority, dependencies, hashes,
  receipts, lightweight media/text properties, and related machine-readable
  evidence without default raster review.
- `visual` verification is available only through explicit user selection and
  creates only minimum selected-artifact review representations. Selected public
  images remain distinct from temporary inspection artifacts.
- The Phase 49.3 presentation-semantics Work Product state is consumed rather
  than duplicated.
- The matching checklist, focused validation, review, full Cozy validation, and
  release closure complete the whole native Phase 56 sequence. Phase 57 remains
  a separately planned successor.

## Non-goals

- Reopening native provider dispatch or accepted-evidence/currentness semantics
  established by Phases 56 and 56.1.
- Publication Export, a SimpleModeling.org target binding, compatibility
  adapters, manual evidence adoption, publication, deployment, upload, push,
  or commit.
- Default PDF, slide, or video raster review.

## References

- [Phase 56](phase-56.md)
- [Phase 56.1](phase-56.1.md)
- [Phase 56.2 checklist](phase-56.2-checklist.md)
- [Phase 57](phase-57.md)
- `docs/journal/2026/09/2026-09-07-document-project-production-workflow-greenfield-decision.md`
- `docs/spec/document-project.md`
