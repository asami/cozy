# Phase 49.1 Checklist: Document Project Presentation Semantics Workflow Surfaces

Status: IN PROGRESS
phase=[Phase 49.1](phase-49.1.md)

Planning rule: approximate-six-hour packing target; preferred 4–8 h band.

## P491-01: Strict verification

- [x] Route `verify` through the Phase-46 semantic loader/validator.
- [x] Preserve `DP-SEM-*` diagnostics without a permissive alias reader.
- [x] Prove valid semantics are admissible to the Phase-46.1 projection
  boundary and report projection/coverage failure explicitly.

## P491-02: Inspection state

- [x] Expose schema identity, Core binding/currentness, semantic state, Story
  Step/Transition counts, Explanation Structure count, projection availability,
  and coverage state in `inspect`.
- [x] Preserve unavailable values without placeholders or invented success.

## P491-03: Plan and Dashboard

- [x] Place Presentation Semantics between Content Core and dependent semantic
  work in `plan`.
- [x] Block missing, authoring-incomplete, invalid, or stale work
  deterministically without execution.
- [x] Make Dashboard expose the normal production stage and exact first-blocker
  authoring action.
- [x] Prove Dashboard stays read-only and does not author semantic content.

## Closure

- [x] Focused executable specifications cover strict verification and every
  read-only workflow surface.
- [x] Existing Document Project behavior remains compatible outside the newly
  participating semantic authority.
- [ ] Required full Cozy validation and independent Phase review pass.
- [ ] Release closure records only the read-only workflow-surface handoff for
  Phase 49.2.

## Step evidence

- [x] `P491-STEP-REPAIR-VAL-001`: `testOnly
  cozy.document.CozyDocumentProjectSpec` passed 93 specifications with 0
  failures.
- [x] The focused repair closure has zero Current Step Blockers after its
  permitted M0 prose-only repair tail.
- [x] Cozy provides the fixed versioned `software-explanation@1` and
  `presentation@1` Catalogs; Document Project has no Catalog-selection,
  discovery, loading, or fallback responsibility.
