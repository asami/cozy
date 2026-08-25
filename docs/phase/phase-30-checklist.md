# Phase 30 Checklist: Unified Storyboard and Three-Gate Video Review Workflow

This checklist is the authoritative internal Step ledger for the one Phase 30
execution explicitly authorized on 2026-08-26. The estimated Phase effort
remains 10–15 hours at recommended high effort. `P30-00` through `P30-03` are
internal Steps/Slices, not child Phases; no child-phase documents or checklists
are created or required. This ledger does not claim implementation, validation,
review, commit, or completion while the Phase is in progress.

Phase status: IN PROGRESS
Phase Execution Gate: EXPLICIT_SINGLE_PHASE_AUTHORIZED

## P30-00: Single-Phase Authorization and Storyboard Foundation

Status: COMPLETE

- [x] Record the user decision to run the long Phase without splitting it,
      while preserving internal Step/Slice gates and the existing product
      boundary.
- [x] Retain the 10–15h estimate and make the one-Phase delivery shape
      explicit in the Phase, README, and strategy ledger.
- [x] Establish the normative `cozy.video.storyboard.v1` specification and
      the consistent Cozy responsibility design.
- [x] Keep Dox/PPTX/Textus consumer work, successor Phases, runtime acceptance,
      validation, review, commit, and publication outside this authorization.

## P30-01: Storyboard Schema and Markdown / JSON Adapters

Status: PLANNED

- [ ] Freeze `cozy.video.storyboard.v1`, restricted `storyboard.md` grammar,
      equivalent `storyboard.json`, typed semantic model, and normalization.
- [ ] Specify deterministic Markdown/JSON conversion and semantic round-trip
      preservation for every contracted field and scene order.
- [ ] Specify structured rejection diagnostics for schema/version, identity,
      timing, speaker/role, path/ref, and unpreserved unknown-field errors.
- [ ] Keep `storyboard.json` distinct from legacy dialogue `script.json` and
      define explicit, diagnostic migration behavior.

## P30-02: Optional Storyboard Visual-Story Review

Status: PLANNED

- [ ] Define deterministic optional visual-story evidence for Dox/PPTX
      handoff, including scene diagrams and project-owned assets.
- [ ] When requested, require visual-story evidence to preserve Storyboard
      scene order, narration, screen content, captions, timing, and direction.
- [ ] Record exact Storyboard, diagram/asset, evidence, and optional handoff
      identities only when visual-story review is requested.
- [ ] Reject confirmation/final generation when the Storyboard is stale,
      changed, missing, or unapproved; reject explicitly reviewed visual inputs
      when their accepted visual evidence is stale.
- [ ] Preserve the rule that ordinary storyboard and confirmation-video review
      does not require image-backed visual-story slides.

## P30-03: Approved Storyboard Confirmation/Final Build and Final Review

Status: PLANNED

- [ ] Add `parts[].storyboard` for Markdown and JSON and route all video stages
      through the same normalized Storyboard.
- [ ] Remove source `script.json` from new scaffolds; keep any serialized
      execution handoff in generated `target/cozy-video` output only while
      retaining legacy `parts[].script` support pending an accepted migration.
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
- [ ] Prove a Reimu/Marisa flow end to end from `storyboard.md`, then from
      equivalent external `storyboard.json`; add visual-story slides only to
      the explicit visual-inspection variant.
- [ ] Run the focused/full regression, legacy migration checks, runtime
      acceptance, independent review, and final ledger convergence selected for
      this Step when it is admitted for implementation.

Phase 30 closes only after P30-00, P30-01, P30-02, and P30-03 close and the
normal Phase closure gates pass. Closure requires the accepted workflow to
prove Storyboard review, confirmation-video review, and final-video evidence
review without a source-managed `script.json`; it does not use child-phase
closure as a gate.
