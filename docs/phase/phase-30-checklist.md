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

Status: COMPLETE

- [x] Freeze `cozy.video.storyboard.v1`, restricted `storyboard.md` grammar,
      equivalent `storyboard.json`, typed semantic model, and normalization.
- [x] Specify deterministic Markdown/JSON conversion and semantic round-trip
      preservation for every contracted field and scene order.
- [x] Specify structured rejection diagnostics for schema/version, identity,
      timing, speaker/role, path/ref, and unpreserved unknown-field errors.
- [x] Keep `storyboard.json` distinct from legacy dialogue `script.json` and
      define explicit, diagnostic migration behavior.

## P30-02: Optional Storyboard Visual-Story Review

Status: COMPLETE

- [x] Define deterministic optional visual-story evidence for Dox/PPTX
      handoff, including scene diagrams and project-owned assets.
- [x] When requested, require visual-story evidence to preserve Storyboard
      scene order, narration, screen content, captions, timing, and direction.
- [x] Record exact Storyboard, diagram/asset, evidence, and optional handoff
      identities only when visual-story review is requested.
- [x] Reject confirmation/final generation when the Storyboard is stale,
      changed, missing, or unapproved; reject explicitly reviewed visual inputs
      when their accepted visual evidence is stale.
- [x] Preserve the rule that ordinary storyboard and confirmation-video review
      does not require image-backed visual-story slides.

## P30-03: Approved Storyboard Confirmation/Final Build and Final Review

Status: COMPLETE

- [x] Add `parts[].storyboard` for Markdown and JSON and route all video stages
      through the same normalized Storyboard.
- [x] Remove source `script.json` from new scaffolds; keep any serialized
      execution handoff in generated `target/cozy-video` output only while
      retaining legacy `parts[].script` support pending an accepted migration.
- [x] Build a confirmation video and a separate final video only from approved
      Storyboard identities; confirmation output never overwrites final output.
- [x] Define confirmation/final mode-specific artifact paths, manifests, and
      lifecycle state without assigning Phase 31 encoding-policy values here.
- [x] Reuse unchanged narration/audio and render chunks only when their scene
      inputs, renderer settings, and narration configuration match; prove
      deterministic invalidation when an input changes.
- [x] Generate independent rendered-video review evidence for the final MP4,
      keeping audiovisual acceptance separate from Storyboard approval.
- [x] Generate a video-derived review PPTX only when explicitly requested for
      distribution, meeting, handoff, or archive use; its absence is not an
      error and does not block final-review state.
- [x] Prove a Reimu/Marisa flow end to end from `storyboard.md`, then from
      equivalent external `storyboard.json`; add visual-story slides only to
      the explicit visual-inspection variant.
- [x] Run the focused regression, legacy migration checks, runtime
      acceptance, independent review, and final ledger convergence selected for
      this Step. Repository-full regression remains the Phase release gate.

### P30-03A: Reimu/Marisa Representation-Equivalence Acceptance

Status: COMPLETE

- [x] Prove an ordered two-scene Reimu then Marisa flow end to end from
      `storyboard.md` and equivalent `storyboard.json`, including confirmation,
      final, and review-evidence binding without a source-managed `script.json`.

### P30-03B: Runtime Credit-Hold Effective-FPS Conformance

Status: COMPLETE

- [x] Runtime integration proves a one-second credit hold resolves at the
      effective encoding fps for Storyboard confirmation/final flow.

### P30-03C: Real-toolchain Web-demo Fixture

Status: COMPLETE

- [x] The real-toolchain profile provisions a direct valid Web-demo recording
      and validates the three-part Storyboard confirmation/final flow.

### P30-03D: Output Identity and Assembly Boundary Repair

Status: COMPLETE

- [x] Cache identity tolerates missing output parents and preserves the
      established direct external output path while assembly still rejects
      direct/symlink violations before tool execution.

### P30-03 focused re-review findings

Status: RESOLVED — P30-03 COMPLETE; Phase 30 remains IN PROGRESS

The authorized P30-03 repair first closed `CB-001` through `CB-004`; its
focused executable-specification receipt `47785-20260826T022306Z` passed 148
tests with 0 failures (3 Docker-gated tests canceled). The following additional
current-boundary blockers were then recorded and separately authorized. The
repair receipt `90102-20260826T033826Z` passed 153 tests with 0 failures (the
same 3 feature-gated integration tests canceled), and its independent focused
re-review found no new finding. The final bounded repair then closed
`P30-03-CB-010`: external final and renderable-part output parent chains are
created and validated as direct non-symlink directories before a tool or credit
write can use them. Its focused receipt `63125-20260826T062158Z` passed 46
tests with 0 failures, and its independent focused closure review found no
Current Boundary Blocker. This records convergence of `CB-005` through
`CB-010` and completes P30-03. It does not close Phase 30.

- [x] `P30-03-CB-005` / `CB-005`: make final-review evidence validate complete
      final-manifest provenance: canonical identity and serialization, required
      Storyboard/cache/handoff fields, and rejection of unknown or incomplete
      fields. Reject a symlinked `target/cozy-video/final` directory whose
      resolved target escapes the project before accepting its manifest.
- [x] `P30-03-CB-009` / `CB-009`: make the confirmation gate verify direct,
      project-contained ancestor directories as well as direct leaf files.
      A symlinked `target/cozy-video/confirmation` directory currently lets a
      confirmation MP4 and manifest resolve outside the generated-artifact
      boundary while still being accepted for final approval.
- [x] `P30-03-CB-006` / `CB-006`: include all output-affecting project inputs,
      including `title` and `characters`, in Storyboard cache identity so a
      changed title or character definition cannot reuse stale narration or
      rendered output.
- [x] `P30-03-CB-007` / `CB-007`: have Cozy generate a mode-local provenance
      record for every renderable Storyboard or legacy part before accepting a
      mode-cache hit, including a mixed legacy/Storyboard project, so changed
      or missing part artifacts cannot authorize a stale final output.
- [x] `P30-03-CB-008` / `CB-008`: preserve the ordered, lossless pronunciation
      notes contract when projecting a Storyboard to `VideoScript`; reject or
      explicitly model conflicting duplicate surfaces rather than silently
      overwriting a reading in a `Map`.
- [x] `P30-03-CB-010` / `CB-010`: reject a symbolic-link ancestor of an
      external final or renderable-part output before assembly, renderer, or
      credit output writes can use the configured path, while preserving direct
      external outputs and safely creating missing direct parent directories.
- [ ] `P30-03-HYG-001`: update the touched
      `CozyVideoReviewEvidence.scala` source header to the current repository
      date during a separately authorized maintenance change. This is
      non-behavioral and does not mitigate `CB-005` through `CB-010`.

Phase 30 closes only after P30-00, P30-01, P30-02, and P30-03 close and the
normal Phase closure gates pass. Closure requires the accepted workflow to
prove Storyboard review, confirmation-video review, and final-video evidence
review without a source-managed `script.json`; it does not use child-phase
closure as a gate.
