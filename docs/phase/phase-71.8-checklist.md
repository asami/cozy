# Phase 71.8 Checklist

Phase status: IN_PROGRESS
Updated: 2026-10-12
Ledger for: [Phase 71.8](phase-71.8.md)
Primary owner: Cozy

P718-01 implementation is accepted under
`p718-01-qcontract-implementation01@1`, with180 successful specifications,
current eight-file JA/EN listening and independent Step review PASS.
Its exact local Step commit remains outstanding. Phase closure is not claimed.
Phase71.7 retains its predecessor history and obligations
at their original scope.

## P718-01: Profile contract and executable specifications

Stage Status:
- Current status: IN_PROGRESS
- Owner: Cozy
- Update rule: close against the following selected contracts and specification results

- [x] One quality profile resolves picture and audio settings, distinct from presentation profiles and tool/encoder selection.
- [x] The shifted ladder maps current lightweight to new standard, current standard to new quality and current quality to the upper tier; omitted selection retains the current default picture settings under new standard.
- [x] A new lightweight practice profile specifies lower picture quality and faster generation than new standard, with meaningful storyboard/layout and narration review.
- [x] Default selection, existing descriptor compatibility, preset values, individual overrides and configuration precedence are explicit in spec/design.
- [x] Given / When / Then executable specifications cover profile resolution, supported overrides and invalid settings, including existing software/hardware behavior.
- [x] Parent-selected `CozyVideoQualityProfileSpec` and the declared focused accumulator pass with runner/serial-wrapper terminal evidence.
- [x] The managed test-only JA/EN helper compares320k/96k/128k/192k from selected lossless WAV inputs, retains actual sizes/full decode and current attributable human listening accepts candidate audio before Step REVIEW/commit.
- [ ] Independent P718-01 acceptance review and its exact local Step commit complete.

Contract/design: [joint quality specification](../spec/video-quality-profiles.md),
[resolution design](../design/video-quality-profiles.md). The default is new
standard18fps720pCRF32 with listening-accepted128k audio. Explicit legacy selection
keeps historical picture and320k intent; valid canonical selection precedes
valid legacy, with independent field overrides and complete origins.
The [current acceptance evidence](phase-71.8.md#p718-01-acceptance-evidence)
records180 passing specifications, all eight natural JA/EN outputs and independent
Step review PASS. Practice speed and useful output remain P718-03 duties; they
are not inferred from the table/specifications. The combined review/commit
checkbox remains open until the exact local Step commit succeeds.

## P718-02: Shared resolution across encoding routes

Stage Status:
- Current status: OPEN
- Owner: Cozy
- Update rule: close against actual affected route behavior and its executable specifications

- [ ] Native/mixed encoding, Docker Remotion mux and final assembly honor the same resolved audio setting; final assembly does not replace a selected bitrate with320k.
- [ ] `video inspect` and relevant generated records report the chosen profile and effective picture/audio settings with their origins.
- [ ] Lossless intermediate audio, the single final AAC encode,48kHz stereo timing and complete end-card behavior are preserved.
- [ ] Independent P718-02 acceptance review, proportional validation and exact local Step commit complete.

## P718-03: Quality, size and regression acceptance

Stage Status:
- Current status: OPEN
- Owner: Cozy
- Update rule: close only with actual listening/media evidence, validation and review/commit results

- [ ] Selected JA/EN baseline/candidate listening and actual size measurements establish acceptable audio preset values from lossless inputs.
- [ ] Comparable actual render/encode/total measurements show the practice profile generates faster than new standard on the same selected inputs, route and encoder; reduced size alone is not counted as speed acceptance.
- [ ] Representative direct/final outputs and an explicit override exhibit the selected settings, decode successfully and preserve the common media ending.
- [ ] Affected focused executable specifications and the Phase's declared final validation pass; earlier71.7 acceptance is retained at its original scope.
- [ ] Independent review and required local acceptance/release commits are complete, with any remaining work explicitly recorded.
- [ ] Independent P718-03 acceptance review, proportional validation and exact local Step commit complete.

## Standalone Phase release boundary

Stage Status:
- Current status: OPEN
- Owner: Cozy parent workflow
- Update rule: close only on actual final candidate evidence and distinct bound local release commit

- [ ] All three Step contracts, current audio listening and comparable practice measurements are accepted with exact local Step commits.
- [ ] One comprehensive Phase review in the current PLAN epoch passes; accepted finding/Hygiene/Development Candidate journals retain their required dispositions.
- [ ] Final-only full Cozy `test` passes on the release candidate through the registered serialized runner.
- [ ] Final `cozyExportRuntimeClasspath` succeeds on the same candidate with terminal execution and lock-release evidence.
- [ ] A distinct bound local release commit and canonical Phase/checklist/journal closure are complete;71.7 history and unrelated changes are preserved.

Stop after exactly71.8. External source/shared-skill changes, successor Phases,
public publication, deployment and push remain excluded.
