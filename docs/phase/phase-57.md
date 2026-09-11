# Phase 57: Document Project Publication Export Admission

Status: COMPLETE

Plan date: 2026-09-07
Split approval date: 2026-09-10
Implementation started: 2026-09-11
Closure prepared: 2026-09-11
Development item: DEV-020
Successor: [Phase 57.1](phase-57.1.md)

## Goal

Freeze the Document Project-owned admission boundary for a public publication
export. It selects only current public Work Products explicitly admitted for a
target and rejects all private, stale, partial, unsafe-path, or unreceipted
input before any consumer bundle exists.

## Provenance and structural gate

This is the retained source Phase of the 2026-09-10 approved sequential split:
Phase 57, then [Phase 57.1](phase-57.1.md), [Phase 57.2](phase-57.2.md), and
[Phase 57.3](phase-57.3.md). No execution history was moved: every stage is
still planned. The split preserves the original public-export goal while
separating its four semantic ownership boundaries.

Phase Plan Gate: PROCEED

- target: approximate-six-hour packing target; preferred 4–8 h band
- estimate calibration: Phase 56 had the same Document Project
  producer/evidence/currentness architecture and required a 19–25 h pre-split
  estimate; it is structural, not a measured per-child comparator
- planning_demand: protected-decision
- recommended_parent_profile: gpt-5.6-terra / xhigh
- profile_cost_role: expensive reasoning kernel
- expensive_reasoning_kernel: public export grammar, selection authority,
  exclusion boundary, and fail-closed error semantics
- frozen_profile_transition_handoff: generic export-admission contract for
  Phase 57.1; it permits portable manifest/output construction without
  reopening selection or private-state access
- parent_reasoning_mode_policy: standard
- estimated_at_recommended_profile: 5–6 h; within preferred band
- incoming_handoff: none
- merge_attempts_for_every_sub_4h_child: none
- adjacent_merge_structural_rejection_evidence: Phase 57 + Phase 57.1 is
  10–13 h, above the <=8 h ceiling
- profile_cost_only_rejection_forbidden: true
- short_child_exception: none
- overhead_tradeoff: separating admission from manifest binding keeps the
  consumer-visible contract reviewable without widening the public selector
- agent_reasoning_mode_policy: default standard; use the approved parent
  profile only for the protected contract decision
- runtime_suitability: re-evaluate in the Phase execution task
- source: user-approved Phase 57 split

## In-scope work

| ID | Outcome | Status |
| --- | --- | --- |
| P57-01 | Freeze the public export command and target admission contract; select only admitted current public Work Products whose production evidence is the exact embedded `receipt` object in valid accepted `cozy.document-operation-attempt.v2` evidence under the Phase 56.1 accepted-evidence/currentness contract, normalize output paths deterministically, and reject stale, partial, private, unsafe-path, or unreceipted input. Generated-review receipts and standalone receipt files are nonqualifying. | complete |

## Frozen handoff to Phase 57.1

- kind: authority
- input: generic export-admission contract
- producing action: close P57-01
- output: contract permitting Phase 57.1 to build a portable manifest and
  output bundle without accessing private Document Project state
- owner: Cozy Document Project
- invalidated by: a change to selection, privacy classification, source
  currentness, production evidence, or normalized-path constraints

## Closure criteria

- The public export command has one explicit target/admission contract.
- Only admitted, current, public Work Products with the exact embedded
  `receipt` object in valid accepted `cozy.document-operation-attempt.v2`
  evidence under the Phase 56.1 accepted-evidence/currentness contract can
  cross the boundary. Generated-review receipts and standalone receipt files
  are nonqualifying.
- Private authorities, dialogue, candidate history, Operation Attempts, review
  evidence, raw media, and generated state caches cannot cross it.
- Deterministic path normalization and fail-closed rejection cover stale,
  partial, private, unsafe-path, and unreceipted input.
- The frozen handoff is sufficient for Phase 57.1 without reopening admission.
- P57-01 is complete only with the focused export-admission validation,
  independent review, final full Cozy validation, and distinct phase-release
  closure recorded together; implementation entries alone cannot close it.

## Dependencies

- [Phase 49.3](phase-49.3.md) supplies the completed Document Project
  stale/currentness and Article-9-shaped local-acceptance boundary that this
  selector preserves.
- [Phase 55](phase-55.md) supplies the completed effective site-root/site-config
  authority required by the later Phase 57.2 SimpleModeling.org target binding.
- [Phase 56.2](phase-56.2.md) supplies the completed accepted-evidence,
  derived-currentness, closed executable-state, and structural-verification
  foundation consumed by this admission boundary.

## Closure evidence

- P57-01 was accepted as a Step at
  `ad51aa6842c257120e6abdbc003e41bd0c4182b8`
  (`feat(document-project): admit public export metadata`).
- Focused export-admission and native-evidence specifications passed with 6 and
  8 tests respectively. The Step finding was repaired and the focused
  re-review is clean.
- The mandatory Phase 57 full review is clean with no Current Phase Blocker.
- The final full Cozy suite passed on the release-closure tree: 1,814
  succeeded, 0 failed, 135 suites completed, 0 aborted, and the SBT lock was
  released (`P57-FINAL-FULL-001`).
- This document and its checklist are the closure payload of the distinct
  Phase 57 release commit; that commit establishes this COMPLETE status.

## Non-goals

- Manifest/receipt binding and export currentness, owned by Phase 57.1.
- SimpleModeling.org target binding, owned by Phase 57.2.
- Publication-preparation skill work, site preparation, deployment, upload,
  push, or production-site mutation, owned or excluded by Phase 57.3.
- Compatibility readers, migration modes, bridges, dual workflow authority,
  manual evidence adoption, or fabricated successful attempts.

## References

- [Phase 57 checklist](phase-57-checklist.md)
- [Phase 57.1](phase-57.1.md)
- `docs/journal/2026/09/2026-09-07-document-project-production-workflow-greenfield-decision.md`
- `docs/spec/document-project.md`
