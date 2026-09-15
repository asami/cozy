# Phase 61: Video Speech Normalization and Post-Utterance Timing

Status: PLANNED
Plan recorded: 2026-09-14
Development item: DEV-029
Plan Gate: PENDING; promoted contracts and executable specifications precede implementation

## Purpose and boundary

Provide speech-only removal of Japanese middle dots and scene-authored
post-utterance silence that follows actual synthesized audio length.
Keep displayed terminology unchanged and retain the current scene until its
configured trailing interval finishes.

This is an independent video-authoring boundary over the existing Phase 30
workflow and Phase 31 encoding/timing infrastructure. It preserves Phase 59's
priority, separate Phase 60 planning and closed prior behavior.

## Planning inputs and completion ledger

- [Proposal](../notes/video-speech-normalization-and-tail-silence-proposal.md)
- [Decision journal](../journal/2026/09/2026-09-14-video-middle-dot-and-tail-silence-decision.md)
- [Checklist](phase-61-checklist.md): sole completion ledger

The proposal and journal are not normative implementation specifications.
P610-01 promotes the admitted policies before code changes.

## Subphase 61A: Reading and trailing-pause vertical slice

| Step | Observable outcome | Status | Closure basis |
| --- | --- | --- | --- |
| P610-01 | Frozen speech/display, nested-scene and timing contracts with executable specifications | OPEN | Checklist P610-01 |
| P610-02 | Opt-in speech-only middle-dot policy, including dictionary-generated readings | OPEN | Checklist P610-02 |
| P610-03 | Scene-authored trailing silence integrated with actual audio and legacy target timing | OPEN | Checklist P610-03 |
| P610-04 | Supported Storyboard/render/assembly/evidence/currentness routes preserve both policies | OPEN | Checklist P610-04 |
| P610-05 | Isolated driver, regression validation and independent Phase closure evidence | OPEN | Checklist P610-05 |

## Acceptance and exclusions

Acceptance demonstrates unchanged display strings, corrected provider reading,
a requested final post-utterance pause, matching audio/picture/evidence timing,
and independently preserved summary/credits holds with backward-compatible
omitted settings.

Global punctuation rewriting, caption typography redesign, PDF/slides,
content authoring, external-provider changes, external-project edits, site
registration/build, upload, publication, deployment and push are excluded.
Planning does not rebuild the current SimpleModeling.org video.

## Current handoff

Every implementation Step is OPEN. Begin at P610-01. Read the proposal and
affected specs; promote the bounded contract, then implement and validate
through the checklist. A plan is not release or consumer-acceptance evidence.
