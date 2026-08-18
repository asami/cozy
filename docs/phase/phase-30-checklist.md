# Phase 30 Checklist: Unified Storyboard and Three-Gate Video Review Workflow

This checklist is the authoritative parent ledger for Phase 30 planning. It is
not a normative contract and does not authorize implementation before the
required child-phase split.

Phase Plan Gate: SPLIT_REQUIRED

- target per child phase: conservative upper bound <= 6h
- estimated total at recommended effort: 10–15h
- recommended minimum effort: high

## P30-01: Phase 30.1 Storyboard Contract

Status: PLANNED

- [ ] Add Phase 30.1 plan/checklist with a <= 6h upper bound.
- [ ] Freeze `cozy.video.storyboard.v1`, restricted `storyboard.md` grammar,
      equivalent `storyboard.json`, typed Scala model, and normalization.
- [ ] Specify deterministic Markdown/JSON conversion and semantic round-trip
      preservation for all fields and ordering.
- [ ] Specify validation and structured diagnostics for schema, identity,
      timing, speaker/role, asset path, and unpreserved unknown-field errors.
- [ ] Separate `storyboard.json` from legacy dialogue `script.json` and define
      explicit migration behavior.

## P30-02: Phase 30.2 Optional Visual-Story Review

Status: PLANNED

- [ ] Add Phase 30.2 plan/checklist with a <= 6h upper bound.
- [ ] Define deterministic optional visual-story evidence for Dox/PPTX
      generation, including scene diagrams and project-owned assets.
- [ ] When requested, require visual-story slides to preserve Storyboard scene
      order, narration, screen content, captions, timing, and visual intent.
- [ ] Record exact Storyboard, diagram/asset, evidence, and PPTX identities in
      review state only when visual-story review is requested.
- [ ] Reject confirmation/final generation when the Storyboard is stale,
      changed, missing, or unapproved; reject explicitly reviewed visual inputs
      when their accepted visual evidence is stale.
- [ ] Prove that storyboard review and confirmation-video review do not require
      visual-story slides.

## P30-03: Phase 30.3 Confirmation/Final Video Build and Final Review

Status: PLANNED

- [ ] Add Phase 30.3 plan/checklist with a <= 6h upper bound.
- [ ] Add `parts[].storyboard` for Markdown and JSON and route all video stages
      through the same normalized Storyboard.
- [ ] Remove source `script.json` from new scaffolds; keep any serialized
      execution handoff in generated `target/cozy-video` output only.
- [ ] Build a confirmation video and a separate final video only from approved
      Storyboard identities; confirmation output never overwrites final output.
- [ ] Define confirmation/final mode-specific artifact paths, manifests, and
      lifecycle state without assigning Phase 31 encoding-policy values here.
- [ ] Reuse unchanged narration/audio and render chunks only when their scene
      inputs, renderer settings, and narration configuration match; prove
      deterministic invalidation when an input changes.
- [ ] Generate independent rendered-video review evidence for the final MP4,
      keeping audiovisual acceptance separate from Storyboard approval.
- [ ] Generate a video-derived review PPTX only when explicitly requested for
      distribution, meeting, handoff, or archive use; its absence is not an
      error and does not block final-review state.
- [ ] Prove a Reimu/Marisa flow end to end:
      `storyboard.md review -> confirmation video review -> final video ->
      rendered-video evidence review`, then repeat from equivalent external
      `storyboard.json`; add visual-story slides only to the explicit visual
      inspection variant.
- [ ] Run focused/full regression, legacy migration checks, runtime acceptance,
      independent review, and final ledger convergence.

Phase 30 closes only after all three child phases close and the accepted
workflow proves Storyboard review, confirmation-video review, and final-video
evidence review without a source-managed `script.json`.
