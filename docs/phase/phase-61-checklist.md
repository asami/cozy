# Phase 61 Checklist

Phase status: PLANNED
Updated: 2026-09-14
Scope: Subphase 61A, Video Speech Normalization and Post-Utterance Timing
Ledger for: [Phase 61](phase-61.md)

All implementation items are OPEN. Completion requires the linked artifacts
and executable/observational evidence, not planning text.

## P610-01: Contract and executable-spec admission

Stage Status:

- Current status: OPEN
- Owner: Cozy video implementation owner
- Update rule: close only when every item in this section is checked with evidence

- [ ] Promoted design/spec fixes speech-only middle-dot semantics, opt-in/default behavior, dictionary ordering and the exact admitted authoring fields.
- [ ] Promoted timing contract distinguishes requested from effective trailing silence, preserves existing duration precedence, and excludes double padding and summary-hold reuse.
- [ ] Nested-scene inheritance/explicit-zero override, supported round trips, validation and historical-manifest compatibility have executable specifications.
- [ ] Given/When/Then and appropriate property-based specifications fix formula, zero/default, malformed-input and frame-quantization behavior before code changes.

## P610-02: Speech-only middle-dot normalization

Stage Status:

- Current status: OPEN
- Owner: Cozy video implementation owner
- Update rule: close from provider-input and source-preservation specification evidence

- [ ] Enabled middle-dot policy removes U+30FB from original speech text and dictionary-generated readings without recursive substitution.
- [ ] Displayed line/caption/headings and article/glossary spelling are unchanged.
- [ ] Omitted/false settings preserve current Japanese/English behavior, other punctuation and existing whitespace policies.
- [ ] Invalid settings produce structured diagnostics before provider I/O.

## P610-03: Authored trailing silence and effective timing

Stage Status:

- Current status: OPEN
- Owner: Cozy video implementation owner
- Update rule: close from actual-audio and effective-scene-timing specification evidence

- [ ] Scene tailSilence is optional, finite and nonnegative, defaults to zero, and has specified nested inheritance and explicit-zero override.
- [ ] Actual WAV length plus leading/requested trailing time determines the effective scene boundary under the frozen target-minimum formula.
- [ ] Generated audio-manifest tailSilence remains actual effective trailing time; combined audio contains that interval exactly once.
- [ ] The same scene image/caption remains displayed with idle/mouth-closed character during the trailing interval before transition.
- [ ] Zero/absent tails, longer target floors, changed utterance lengths and invalid inputs satisfy the preserved compatibility/failure contract.

## P610-04: Pipeline, evidence and currentness integration

Stage Status:

- Current status: OPEN
- Owner: Cozy video pipeline owner
- Update rule: close from supported producer/consumer and currentness evidence

- [ ] Script/Storyboard conversions and supported synthesis/render/build routes retain both admitted fields without changing semantic display sources.
- [ ] Renderer boundaries and deterministic review evidence agree with audio timing and record requested/effective trailing intervals under compatible schema handling.
- [ ] Opening, summary and credits holds remain independently configured; assembly/effects add no duplicate requested interval.
- [ ] Reading/timing policy changes invalidate incompatible generated audio/manifests/video/evidence through existing identity contracts.
- [ ] Configuration/generation failure preserves the previous successful output and reports exact source/currentness mismatch without approving it.

## P610-05: Isolated acceptance and closure

Stage Status:

- Current status: OPEN
- Owner: Cozy Phase owner; independent reviewer separate from implementation
- Update rule: close only with all preceding sections checked and settled-tree validation/review evidence

- [ ] Isolated Japanese Article-9-shaped fixture speaks compound terms without middle-dot pauses while retaining their displayed spelling.
- [ ] Final utterance with requested 0.8-second tail retains its picture, followed by independently configured silent five-second infographic and credits card.
- [ ] Deterministic fake/local-provider specifications and separate audiovisual evidence cover actual boundaries, idle character behavior and changed audio length; still-frame checks do not claim listening acceptance.
- [ ] Focused and full Cozy validation pass on the settled candidate, and one independent Phase review plus any bounded repair/re-review closes all blockers.
- [ ] Documentation/help and ledger/release records report the exact implemented scope without external-project, publication or consumer-acceptance claims.
