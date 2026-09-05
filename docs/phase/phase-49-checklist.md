# Phase 49 Checklist: Document Project Presentation Semantics Workflow Integration

Phase Status: PLANNED

Predecessors: Phase 46, Phase 46.1, Phase 48

## P49-01: Workflow Work Product and State

- [ ] Register `presentation-semantics` as an `authority` Work Product.
- [ ] Bind Content Core as its direct upstream semantic dependency.
- [ ] Bind dependent article/slide/video semantic work and cross-media
  confirmation downstream without copying the DAG into project descriptors.
- [ ] Derive `missing`, `authoring-incomplete`, `invalid`, `current`, and
  `stale` states deterministically.
- [ ] Prove authoring-incomplete state does not create accepted semantic
  identity, coverage success, or confirmation receipt.

## P49-02: Verify / Inspect / Plan / Dashboard

- [ ] Route `verify` through the existing Phase-46 semantic loader/validator.
- [ ] Preserve `DP-SEM-*` diagnostics without a permissive alias reader.
- [ ] Prove valid semantics are admissible to the Phase-46.1 projection
  boundary.
- [ ] Expose schema, Core binding/currentness, Story Step/Transition counts,
  Structure count, projection availability, and coverage state in `inspect`.
- [ ] Make `plan` place Presentation Semantics between Content Core and
  dependent article/slide/video semantic work.
- [ ] Block dependent work deterministically when semantics are missing,
  incomplete, invalid, or stale according to the accepted state model.
- [ ] Make Dashboard expose Presentation Semantics as a production stage and
  recommend the exact authoring authority/action when it is the first blocker.
- [ ] Prove Dashboard remains read-only and does not author semantic content.

## P49-03: Public Cross-Media Confirmation Route

- [ ] Freeze the public Document Project command/operation grammar for shared
  presentation confirmation.
- [ ] Route the public operation to the accepted Phase-46.1 implementation.
- [ ] Keep `article-review.html` and shared cross-media confirmation distinct.
- [ ] Generate deterministic default confirmation HTML and receipt outputs.
- [ ] Require no package-private Scala API use by Document Project callers.
- [ ] Reuse existing renderer/receipt/coverage behavior; add no parallel
  implementation.

## P49-04: Currentness and Stale Propagation

- [ ] Content Core identity change makes presentation semantics stale.
- [ ] Presentation-semantics stale state propagates to dependent semantic
  projections and confirmation.
- [ ] Presentation-semantics identity change invalidates prior dependent
  projection/confirmation receipt identities.
- [ ] No automatic write-back rewrites stale semantic authority.
- [ ] Receipt currentness remains separate from semantic coverage.
- [ ] A current receipt cannot substitute for coverage verification.

## P49-05: Article 9 Operational Driver

- [ ] Start from a Phase-48-style scaffolded semantic workspace.
- [ ] Author enough real Story Flow / Explanation Structure semantics to pass
  strict validation.
- [ ] Exercise `verify`, `inspect`, `plan`, and Dashboard through the public
  Document Project surface.
- [ ] Generate shared cross-media confirmation through the public route.
- [ ] Exercise at least one upstream identity change and prove stale
  propagation/recovery.
- [ ] Prove no out-of-band hand-built confirmation HTML is required.
- [ ] Keep editorial completion/publication/deployment outside Phase closure.

## Closure

- [ ] Focused executable specifications pass.
- [ ] Existing Document Project review/dashboard behavior remains compatible
  outside the newly participating semantic authority.
- [ ] Required full Cozy validation passes.
- [ ] Independent Phase review converges with no unresolved Current Phase
  Blocker.
- [ ] Release closure records exact evidence without claiming Article 9
  publication or external mutation.
