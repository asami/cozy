# Phase 61.1 Checklist

Phase status: CLOSED
Updated: 2026-09-16
Scope: Subphase 61B, Authored Timing through Acceptance
Ledger for: [Phase 61.1](phase-61.1.md)
Predecessor: [Phase 61](phase-61.md)
Repository-full validation: aggregate final owner for `PHASE-61` -> `PHASE-61.1`

All implementation items are CLOSED. Completion is supported by the
predecessor's committed contract handoff, accepted Step commits, focused
validation, independent review, repair convergence, and the aggregate final
owner's repository-full release validation.

## P610-03: Authored trailing silence and effective timing

Stage Status:

- Current status: CLOSED
- Owner: Cozy video implementation owner
- Update rule: close from actual-audio and effective-scene-timing specification evidence

- [x] Scene tailSilence is optional, finite and nonnegative, defaults to zero, and has specified nested inheritance and explicit-zero override.
- [x] Actual WAV length plus leading/requested trailing time determines the effective scene boundary under the frozen target-minimum formula.
- [x] Generated audio-manifest tailSilence remains actual effective trailing time; combined audio contains that interval exactly once.
- [x] The same scene image/caption remains displayed with idle/mouth-closed character during the trailing interval before transition.
- [x] Zero/absent tails, longer target floors, changed utterance lengths and invalid inputs satisfy the preserved compatibility/failure contract.

Evidence: accepted Step commit 9109b41 and its focused validation/review
evidence.

## P610-04: Pipeline, evidence and currentness integration

Stage Status:

- Current status: CLOSED
- Owner: Cozy video pipeline owner
- Update rule: close from supported producer/consumer and currentness evidence

- [x] Script/Storyboard conversions and supported synthesis/render/build routes retain both admitted fields without changing semantic display sources.
- [x] Renderer boundaries and deterministic review evidence agree with audio timing and record requested/effective trailing intervals under compatible schema handling.
- [x] Opening, summary and credits holds remain independently configured; assembly/effects add no duplicate requested interval.
- [x] Reading/timing policy changes invalidate incompatible generated audio/manifests/video/evidence through existing identity contracts.
- [x] Configuration/generation failure preserves the previous successful output and reports exact source/currentness mismatch without approving it.

Evidence: accepted Step commit 73929546 and its focused validation/review
evidence.

## P610-05: Isolated acceptance and closure

Stage Status:

- Current status: CLOSED
- Owner: Cozy Phase owner; independent reviewer separate from implementation
- Update rule: close only with all preceding sections and settled-tree validation/review evidence

- [x] Isolated Japanese Article-9-shaped fixture speaks compound terms without middle-dot pauses while retaining their displayed spelling.
- [x] Final utterance with requested 0.8-second tail retains its picture, followed by independently configured silent five-second infographic and credits card.
- [x] Deterministic fake/local-provider specifications and separate audiovisual evidence cover actual boundaries, idle character behavior and changed audio length; still-frame checks do not claim listening acceptance.
- [x] Focused and full Cozy validation pass on the settled candidate, and one independent Phase review plus any bounded repair/re-review closes all blockers.
- [x] Documentation/help and ledger/release records report the exact implemented scope without external-project, publication or consumer-acceptance claims.

Evidence: accepted Step commit dab1e347; focused repair validation receipt
PHASE-61.1-CPB-P611-01-REPAIR-VAL-001; full-review blocker CPB-P611-01
resolved by the focused re-review; aggregate final-owner repository-full
validation is bound by the Phase release receipt.

## Phase closure evidence

- [x] All three Steps have accepted commits: 9109b41, 73929546, and dab1e347.
- [x] The mandatory Phase full review recorded CPB-P611-01; one bounded repair
  cycle and its focused re-review resolved it without an architecture or scope
  expansion.
- [x] HYG-P611-001 is recorded in the canonical Phase hygiene journal as
  nonblocking pre-existing executable-specification organization debt.
- [x] The Phase owns and records the final-only aggregate repository-full SBT
  validation for PHASE-61 -> PHASE-61.1 in the release closure.
