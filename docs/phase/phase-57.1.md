# Phase 57.1: Document Project Export Manifest and Currentness

Status: PLANNED

Plan date: 2026-09-10
Split from: [Phase 57](phase-57.md)
Depends on: Phase 57
Successor: [Phase 57.2](phase-57.2.md)
Development item: DEV-020

## Goal

Make the admitted public export portable and verifiable. Bind its selected Work
Products, roles, media types, paths, and exact bytes in a versioned manifest
and receipt; derive its currentness from every authority without exposing
Document Project private state to consumers.

## Provenance and structural gate

This second child consumes the frozen Phase 57 generic export-admission
contract. It does not reopen which Work Products are public or admissible.

Phase Plan Gate: PROCEED

- target: approximate-six-hour packing target; preferred 4–8 h band
- planning_demand: protected-decision
- recommended_parent_profile: gpt-5.6-terra / xhigh
- profile_cost_role: expensive reasoning kernel
- expensive_reasoning_kernel: versioned manifest/receipt grammar and the
  authority graph defining exported-bundle currentness
- frozen_profile_transition_handoff: a generic verified public export bundle
  that Phase 57.2 can validate without Document Project internals
- parent_reasoning_mode_policy: standard
- estimated_at_recommended_profile: 5–7 h; within preferred band
- incoming_handoff: Phase 57 export-admission contract
- merge_attempts_for_every_sub_4h_child: none
- adjacent_merge_structural_rejection_evidence: Phase 57 + Phase 57.1 is
  10–13 h and Phase 57.1 + Phase 57.2 is 10–13 h, both above the <=8 h ceiling
- profile_cost_only_rejection_forbidden: true
- short_child_exception: none
- overhead_tradeoff: one portable receipt stops target consumers from
  reconstructing source selection or private state
- agent_reasoning_mode_policy: default standard; use the approved parent
  profile only for the protected contract decision
- runtime_suitability: re-evaluate in the Phase execution task
- source: approved split from Phase 57

## In-scope work

| ID | Outcome | Status |
| --- | --- | --- |
| P571-01 | Emit a versioned target/Work Product/hash/role/media/path manifest and receipt binding the manifest and exported bytes. | planned |
| P571-02 | Derive export current/stale state from source authority, selection, production receipt, and exported-byte changes. | planned |
| P571-03 | Let a consumer validate the bundle without reading or reconstructing private Document Project state. | planned |

## Frozen handoff to Phase 57.2

- kind: authority
- input: manifest/receipt/currentness schema
- producing action: close P571-01 through P571-03
- output: generic verified public export bundle a target consumer can validate
  without private internals
- owner: Cozy Document Project
- invalidated by: a changed source selection, production evidence, manifest
  authority, or exported byte

## Closure criteria

- The manifest names the target, selected Work Products, exact inputs, public
  roles, media types, and normalized output paths.
- The receipt binds the manifest and precise exported bytes.
- Any authoritative source, selection, production-evidence, or byte change
  invalidates export currentness.
- The frozen generic bundle lets Phase 57.2 validate a target binding without
  private Document Project access.

## Non-goals

- Reopening Phase 57 admission or privacy semantics.
- Site-specific target binding, site preparation, skill work, deployment,
  upload, push, or production-site mutation.
- Compatibility staging, manual evidence adoption, or fabricated evidence.

## References

- [Phase 57](phase-57.md)
- [Phase 57.1 checklist](phase-57.1-checklist.md)
- [Phase 57.2](phase-57.2.md)
- `docs/spec/document-project.md`
