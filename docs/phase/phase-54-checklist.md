# Phase 54 Checklist: CBD Support Semantic Capability Foundation

Status: IN_PROGRESS
phase=[Phase 54](phase-54.md)

Planning rule: approximate-six-hour packing target; preferred 4–8 h band.

## MMD-54-01: Capability inventory

Stage Status:
- Current status: DONE
- Owner: Cozy / parent workflow
- Current step: MMD-54-01A
- Update rule: Preserve the existing parent reconciliation, focused checks,
  independent review, acceptance, and existing checklist as
  the closure basis.

- [x] Inventory the current semantic IR and generated metadata for every planned Textus CBD Support view capability.
- [x] Classify each capability as guaranteed, faithfully derivable, partially represented, or missing.

Evidence: [CML Semantic Capability Inventory](../notes/cml-semantic-capability-inventory.md),
2026-09-28 static validation and independent MMD-54-01 Step review PASS.
No executable behavior or external consumer acceptance is claimed.

## MMD-54-02: Stable identity and absence

Stage Status:
- Current status: DONE
- Owner: Cozy / parent workflow
- Current step: MMD-54-02A
- Update rule: Preserve the parent reconciliation, focused validation,
  protected-focused Step review, and acceptance; these two items remain the
  closure basis. This does not close the Phase or accept later Steps.

- [x] Define stable source-attributed model-element identities and cross-reference rules.
- [x] Define explicit absence semantics and prohibit name-based identity or policy guessing.

Evidence: [CML Semantic Foundation specification](../spec/cml-semantic-foundation.md),
[design](../design/cml-semantic-foundation.md), and CmlSemanticFoundationSpec;
2026-09-28 representative validation (12 tests, including two 100-case
properties), legacy metadata/Workflow accumulator (93 tests), and independent
protected-focused MMD-54-02 Step review PASS. Versioned publication remains
MMD-54-03; full Phase validation and release closure remain pending.

## MMD-54-03: Versioned publication foundation

Stage Status:
- Current status: DONE
- Owner: Cozy / parent workflow
- Current step: MMD-54-03A
- Update rule: Preserve current-tree validation, protected-focused review,
  bounded repair, independent focused re-review PASS, and the exact acceptance
  commit; these two items remain the closure basis. This does not close the
  Phase or accept MMD-54-04.

- [x] Freeze the consumer-neutral semantic-metadata envelope and extension/compatibility policy.
- [x] Preserve the ownership boundary: Cozy supplies semantics; Textus CBD Support renders and reviews them.

Evidence: [CML Semantic Metadata specification](../spec/cml-semantic-metadata.md),
[design](../design/cml-semantic-metadata.md), the versioned JSON fixture, and
CmlSemanticMetadataSpec; 2026-09-28 representative validation (10 tests,
including two 100-case properties), foundation/legacy Workflow accumulator
(105 tests), protected-focused Step review and independent focused re-review
PASS after one bounded null-extension-key repair cycle. No CML producer, CLI,
runtime integration, or external consumer acceptance is claimed. Full Phase
review, repository-wide validation, and release closure remain pending.

## MMD-54-04: Child handoff

Stage Status:
- Current status: DONE
- Owner: `/root` (parent workflow)
- Current step: MMD-54-04A
- Update rule: The parent updates this stage against the first existing
  MMD-54-04 checklist item. Step acceptance proves the foundation handoff
  only; the separately retained Phase release closure remains open.

- [x] Freeze the inventory/identity/publication handoff for Phases 54.1 through 54.7.

Evidence: [CML Semantic Foundation Handoff](../design/cml-semantic-foundation-handoff.md),
2026-09-28 six-document static validation and independent MMD-54-04 Step
review PASS. The eight-row handoff includes Phase 64; child projections remain
PLANNED. No producer, external consumer acceptance, or Phase closure is claimed.

## Phase 54 release closure (retained MMD-54-04 obligation)

Stage Status:
- Current status: OPEN
- Owner: `/root` (parent workflow)
- Update rule: Keep this retained MMD-54-04 release obligation open until the
  full Phase review, full suite, distinct release commit, and reproducible
  evidence are complete. It is not Step acceptance and is not checked here.

- [ ] Complete focused validation, review, release closure, and reproducible evidence for this foundation child only.
