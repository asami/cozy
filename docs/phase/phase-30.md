# Phase 30: Unified Storyboard and Three-Gate Video Review Workflow

Status: IN PROGRESS

Plan date: 2026-08-18
Execution authorization date: 2026-08-26

Phase Execution Gate: EXPLICIT_SINGLE_PHASE_AUTHORIZED

- estimated total at recommended effort: 10–15h
- recommended minimum effort: high
- runtime suitability: re-evaluate at each internal Step/Slice gate
- source: KnowledgeHub external-reference video authoring follow-up

The user explicitly authorized one long-running Phase 30 execution without
splitting it into child phases. `P30-00` through `P30-03` are internal Steps
and their Slices within this Phase; they are not child Phases `30.1`–`30.3`.
The exception changes delivery shape only. It does not authorize a successor
Phase, work in another repository, Dox/PPTX runtime acceptance, or any claim
of implementation, validation, review, commit, or completion.

## Goal

Make a versioned Storyboard the single content contract for Cozy video
production and establish the following review workflow:

```text
storyboard.md review
  -> optional image-backed visual-story review
  -> confirmation video review
  -> final video and rendered-video evidence review
```

Humans author and review `storyboard.md`. External tools read and write the
same information as `storyboard.json`. Cozy parses either representation into
one typed internal `Storyboard`; a source-managed `script.json` is not required
on the new path.

Image-backed visual-story slides are a generated inspection projection of the
Storyboard, not a second semantic source. They are created only when PNG
frames, diagrams, layout, or image selection need separate review. The normal
path approves `storyboard.md`, then confirms the rendered flow through a
confirmation video. Post-build review remains a separate final gate based on
the final MP4 and rendered-video evidence.

## Intended command surface

```text
cozy video storyboard validate <storyboard.md|storyboard.json>
cozy video storyboard inspect <storyboard.md|storyboard.json>
cozy video storyboard convert <input> --save <output.md|output.json>
cozy video storyboard review-evidence <video-project> --save <dir>
cozy video build <video-project> --mode confirmation
cozy video build <video-project> --mode final
cozy video review-evidence <video-project> --save <dir>
```

The PowerPoint renderer remains the Dox/presentation responsibility. Cozy owns
the normalized Storyboard, deterministic content-review evidence, approval
identity checks, video execution input, and rendered-video review evidence.

## Source and artifact roles

- `review/article-content/storyboard.md`: human authoring and semantic review
  source.
- `storyboard.json`: equivalent external machine-interchange representation.
- `video/video.yaml`: characters, narration provider, renderer, effects,
  project-owned assets, credits, and Storyboard selection.
- image-backed visual-story slides: optional generated projection of the
  selected Storyboard for PNG, diagram, layout, and image inspection.
- internal typed `Storyboard`: common Cozy model for Markdown and JSON.
- generated execution data under `target/cozy-video`: renderer/audio handoff,
  diagnostics, hashes, and caches; never a second source.
- confirmation video: a non-final rendered flow for timing, narration,
  animation, transition, and renderer review; never overwrites the final MP4.
- final MP4 and rendered-video evidence: post-build final-review artifacts.
- video-derived review PPTX: optional distribution, meeting, handoff, or
  archive artifact; never a normal build or final-review prerequisite.

## Boundary and invariants

- Define one public schema, `cozy.video.storyboard.v1`, with lossless Markdown
  and JSON representations.
- Parse both representations into one typed Scala `Storyboard`; validation and
  downstream behavior must not depend on source serialization.
- Preserve scene identity/order, section, speaker, role, planned narration,
  screen heading/content, caption, duration, lead silence, transition,
  production inserts, diagram/asset references, pronunciation notes, and
  direction across conversion.
- Use `storyboard.md` for human review and `storyboard.json` for external input
  and output. Both are first-class Cozy inputs.
- Keep production configuration in `video.yaml`; do not duplicate character,
  provider, renderer, or credit policy in every Storyboard.
- Generate optional visual-story evidence from normalized Storyboard data. When
  requested, its scene order, text, captions, diagrams, assets, and timing
  summary must match the selected Storyboard.
- Record the exact Storyboard identity used by the confirmation and final
  builds; also record diagram/asset and visual-story identities when that
  optional review was requested.
- Refuse confirmation or final video generation when the Storyboard is stale or
  different from the accepted Storyboard identity. When an explicitly reviewed
  diagram or asset changes, require its visual review to be refreshed.
- Generate confirmation and final video only from the approved normalized
  Storyboard. Do not allow a separate source `script.json` to diverge from
  reviewed content.
- Keep confirmation and final outputs separate. Reuse audio and render chunks
  only when their scene inputs, renderer settings, and narration inputs match
  their recorded identities; otherwise invalidate deterministically.
- After final build, create independent rendered-video review evidence. A
  video-derived review PPTX is optional and does not determine final-review
  state. Storyboard approval does not imply audiovisual acceptance.
- Reject missing fields, duplicate IDs, unknown schema versions, invalid
  speakers/roles, unsafe paths, malformed timing, stale approval evidence, and
  unknown fields that cannot be preserved. Never silently discard information.
- A legacy dialogue `script.json` is not `storyboard.json`. Any migration must
  be explicit and diagnostic.

## Non-goals

- Generating or rewriting narration meaning with AI during conversion.
- Treating PowerPoint as an editable semantic source.
- Treating a video-derived review deck as the pre-production Storyboard.
- Moving PowerPoint authoring into Cozy.
- Combining all `video.yaml` production settings into the Storyboard.
- Publishing, uploading, or deploying the resulting video.
- Removing all legacy `parts[].script` support without a separately accepted
  migration decision and regression evidence.

## Internal delivery Steps

The following identifiers are the authoritative internal Step ledger. No child
Phase document or child Phase checklist is created or required.

### P30-00: Single-Phase authorization and Storyboard foundation

Status: COMPLETE

This Step records the explicit delivery-shape authorization and establishes
the normative Storyboard specification/design foundation. Its documentation
Slice P30-00A passed the class-D static checks and clean lightweight Step
review. This completed Step does not claim Storyboard code, CLI, executable
specifications, runtime validation, or Phase completion.

### P30-01: Storyboard Schema and Markdown / JSON Adapters

Status: PLANNED

Own the normative schema, restricted Markdown grammar, JSON representation,
typed model, normalization, validation, conversion, semantic round-trip, and
legacy-script distinction.

### P30-02: Optional Storyboard Visual-Story Review

Status: PLANNED

Own deterministic visual-story evidence, optional diagram/asset inclusion,
Dox/PPTX handoff, review-state identities, and stale-visual-input rejection.
It must not make image-backed slides mandatory for ordinary storyboard or
confirmation-video review.

### P30-03: Approved Storyboard Confirmation/Final Build and Final Review

Status: PLANNED

Own `parts[].storyboard`, inspect/synthesize/render/build integration,
confirmation/final output separation, cache reuse and invalidation, removal of
source `script.json` from new scaffolds, post-build review evidence, optional
video-derived review-deck state, legacy regression, and end-to-end acceptance.

Implementation remains gated by the internal Step/Slice ledger and the
normative specification/design. No implementation, validation, review, commit,
or completion is claimed by this Phase record at its current status.

## Completion criteria

- Equivalent `storyboard.md` and `storyboard.json` normalize to the same typed
  Storyboard and semantic identity.
- Markdown/JSON semantic round-trips preserve every contracted field.
- Optional image-backed visual-story slides, including admitted diagrams, are
  generated from the normalized Storyboard with exact source/evidence hashes.
- Confirmation and final builds reject stale or unapproved Storyboard inputs;
  explicitly reviewed diagram inputs also reject stale visual evidence.
- An approved Storyboard builds a confirmation video and a separate final video
  without a source-managed `script.json`.
- Unchanged audio and render chunks are reused only with matching identities;
  changed scene inputs invalidate their dependent artifacts.
- The final video receives separate deterministic review evidence. A
  video-derived review PPTX is generated only when explicitly requested; final
  review state is independent of optional visual-story and deck artifacts.
- New scaffolds use `storyboard.md`; external JSON input/output remains
  supported through the same schema.
- Focused/full Cozy tests, conversion properties, Dox/PPTX handoff validation,
  runtime smoke, visual/technical review, and ledger convergence pass in the
  respective internal Steps.

## Dependencies

- Phase 8: Cozy Video inspect/synthesize/render/build and review foundations.
- Phase 17: composition profiles, project-owned assets, effects, and renderer
  planning.
- Phase 18: pronunciation handling.
- Phase 20: provider-neutral narration and credit resolution.
- Dox article-content and video-review workflows: optional PPTX generation and
  visual review responsibilities.

## References

- `docs/phase/phase-30-checklist.md`
- `docs/phase/phase-8.md`
- `docs/phase/phase-17.md`
- `docs/phase/phase-18.md`
- `docs/phase/phase-20.md`
- `docs/design/video-storyboard.md`
- `docs/spec/video-storyboard.md`
- `docs/journal/2026/08/2026-08-26-phase-30-single-phase-authorization.md`
