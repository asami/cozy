# Phase 71.8 Checklist

Phase status: CLOSED on 2026-10-13
Updated: 2026-10-13
Ledger for: [Phase 71.8](phase-71.8.md)
Primary owner: Cozy

P718-01 implementation is accepted under
`p718-01-qcontract-implementation01@1`, with180 successful specifications,
current eight-file JA/EN listening and independent Step review PASS.
Its exact local Step commit succeeded as
`1581c8d389dd036cf1717533d69f62c7e28a0114`. P718-01 is CLOSED; Phase closure is not claimed.
Phase71.7 retains its predecessor history and obligations
at their original scope.

## P718-01: Profile contract and executable specifications

Stage Status:
- Current status: CLOSED on 2026-10-12
- Owner: Cozy
- Update rule: close against the following selected contracts and specification results

- [x] One quality profile resolves picture and audio settings, distinct from presentation profiles and tool/encoder selection.
- [x] The shifted ladder maps current lightweight to new standard, current standard to new quality and current quality to the upper tier; omitted selection retains the current default picture settings under new standard.
- [x] A new lightweight practice profile specifies lower picture quality and faster generation than new standard, with meaningful storyboard/layout and narration review.
- [x] Default selection, existing descriptor compatibility, preset values, individual overrides and configuration precedence are explicit in spec/design.
- [x] Given / When / Then executable specifications cover profile resolution, supported overrides and invalid settings, including existing software/hardware behavior.
- [x] Parent-selected `CozyVideoQualityProfileSpec` and the declared focused accumulator pass with runner/serial-wrapper terminal evidence.
- [x] The managed test-only JA/EN helper compares320k/96k/128k/192k from selected lossless WAV inputs, retains actual sizes/full decode and current attributable human listening accepts candidate audio before Step REVIEW/commit.
- [x] Independent P718-01 acceptance review and its exact local Step commit complete.

Contract/design: [joint quality specification](../spec/video-quality-profiles.md),
[resolution design](../design/video-quality-profiles.md). The default is new
standard18fps720pCRF32 with listening-accepted128k audio. Explicit legacy selection
keeps historical picture and320k intent; valid canonical selection precedes
valid legacy, with independent field overrides and complete origins.
The [current acceptance evidence](phase-71.8.md#p718-01-acceptance-evidence)
records180 passing specifications, all eight natural JA/EN outputs and independent
Step review PASS. Practice speed and useful output remain P718-03 duties; they
are not inferred from the table/specifications. Closure basis: the actual
independent PASS, saved/read-back typed ledger, terminal validation/listening
results and native ten-file Step commit above. The later Steps and release
checkboxes remain open.

## P718-02: Shared resolution across encoding routes

Stage Status:
- Current status: CLOSED on 2026-10-12 under `p718-02-routes-implementation01@1`
- Owner: Cozy
- Update rule: close against actual affected route behavior and its executable specifications

- [x] Native/mixed encoding, Docker Remotion mux and final assembly honor the same resolved audio setting; final assembly does not replace a selected bitrate with320k.
- [x] `video inspect` and relevant generated records report the chosen profile and effective picture/audio settings with their origins.
- [x] Lossless intermediate audio, the single final AAC encode,48kHz stereo timing and complete end-card behavior are preserved.
- [x] Independent P718-02 acceptance review, proportional validation and exact local Step commit complete.

Executable route contract:
[CozyVideoQualityRouteSpec](../../src/test/scala/cozy/video/CozyVideoQualityRouteSpec.scala),
plus the focused Storyboard review/build specifications. Joint observations
and legacy missing-field admission are specified in the
[quality wire contract](../spec/video-quality-profiles.md#generated-joint-observations-and-legacy-inputs).
Closure evidence:18 passing route specifications and228 passing accumulator
specifications, terminal SBT/wrapper exit0 and lock release, and independent
focused review `p718-02-focused-step-review-fix04-01@1` PASS after both bounded
review findings were resolved. Typed ledger revision6 was saved and read back.
The three behavior items are verified at this Step's executable route boundary;
actual media/Docker observations and comparable practice speed remain P718-03.
The exact22-file local Step commit succeeded as
`3befd92f53c0bc6919e1e5be2cc52b266e3e0279`. Parent read-back accepted the terminal
native receipt, actual HEAD, ordered parent and exact committed paths; the25
unrelated dirty paths were preserved. Together with the selected review and
validation evidence above, this establishes Step closure. Phase completion
and later acceptance are not implied.

## P718-03: Quality, size and regression acceptance

Stage Status:
- Current status: CLOSED on 2026-10-13
- Owner: Cozy
- Update rule: close only with actual listening/media evidence, validation and review/commit results

Historical checkpoint13/14 (pending acceptance when recorded; final closure basis below supersedes those projections):
Checkpoint13 records all17 selected root03 technical/visual observations:
34 successful original Cozy commands,17 reports/561 actual checks,34 full
decodes and408 individually parent-viewed PNGs. Original texts/labels/opening/
summary/credits are readable and unclipped; H264/AAC stereo48k, rational ends
within one sample, nine/eighteen WAV full≥0.80/tail≥0.60 matches and complete
two-second end cards were observed. Three coherent root03 selectors are
delivered; helper-tests06 passed14 groups in1.034s. Layout04 retains5+163 Scala
successes and one explicit opt-in Docker cancellation at its original scope.
Mixed lightweight/standard warm whole-pair medians are99.736/147.785s (32.5%
shorter), Docker80.229/130.418s (38.5% shorter), with all pairs retained and cold
excluded. The explicit lightweight override observed960×540@15fps/CRF28/
veryfast/AAC192k and common ending. Timing includes native lifecycle/tool/
observer overhead, excludes queue/idle, and does not sum Docker overlap windows.
There are16 complete case-runs plus gap14 on docker-standard/warm02: first
inspection exit0/33 checks/24 viewed PNGs is retained, the duplicate attempt
exited1 before probing at the existing-directory guard, and original initial-
exec/renderer API objects remain missing. Runner completion is INCOMPLETE;
fresh independent P718-03 reassessment must explicitly assess this gap.
Root01 cold measurements/clipping07/wrong-origin05/collision06, root02 failure08
and earlier helper-tests05's14/1.030s result remain history without credit as
root03 output. Independent Step review/commit and all final Phase gates remain
open. Aggregate observations do not establish acceptance, discharge gap14 or
check any P718-03/final release item.
`DEV-P718-03-GENERIC-AUDIO-TIMING-01` remains untouched separate follow-up,
pending independent scrutiny, with all nine original WAVs/lead metadata and
current preservation/usefulness obligations intact. The
[selected protocol and factual history](../journal/2026/10/2026-10-12-phase-71.8-quality-media-acceptance.md)
records the fresh matrix. P718-01 eight JA/EN listening records and P718-02
closed status, validation/review and exact local commits retain their original
scope; all P718-03 items remain unchecked until actual parent admission.

- [x] Selected JA/EN baseline/candidate listening and actual size measurements establish acceptable audio preset values from lossless inputs.
- [x] Comparable actual render/encode/total measurements show the practice profile generates faster than new standard on the same selected inputs, route and encoder; reduced size alone is not counted as speed acceptance.
- [x] Representative direct/final outputs and an explicit override exhibit the selected settings, decode successfully and preserve the common media ending.

Acceptance checkpoint on2026-10-12: independent complete Step review
`p718-03-independent-step-review15@1` PASS, zero current blockers; typed ledger9
saved/read back. This supersedes the preceding checkpoint13/14 pending-review
projection. The first three items are accepted from P718-01 hearing/sizes at
their original scope and current17-case measurements/technical/visual evidence.
Gap14 was independently reassessed from surviving originals; the missing API
objects and old INCOMPLETE runner completion remain unchanged. Generic lead
timing and unused imports are nonblocking separate follow-ups. Proportional
168-spec/14-helper validation retains its original scope. Local Step commit and
all standalone Phase gates remain pending; the three following composite items
remain unchecked until their complete stated obligations are proven.

- [x] Affected focused executable specifications and the Phase's declared final validation pass; earlier71.7 acceptance is retained at its original scope.
- [x] Independent review and required local acceptance/release commits are complete, with any remaining work explicitly recorded.
- [x] Independent P718-03 acceptance review, proportional validation and exact local Step commit complete.

## Standalone Phase release boundary

Stage Status:
- Current status: CLOSED on 2026-10-13
- Owner: Cozy parent workflow
- Update rule: close only on actual final candidate evidence and distinct bound local release commit

- [x] All three Step contracts, current audio listening and comparable practice measurements are accepted with exact local Step commits.
- [x] One comprehensive Phase review in the current PLAN epoch passes; accepted finding/Hygiene/Development Candidate journals retain their required dispositions.
- [x] Final-only full Cozy `test` passes on the release candidate through the registered serialized runner.
- [x] Final `cozyExportRuntimeClasspath` succeeds on the same candidate with terminal execution and lock-release evidence.
- [x] A distinct bound local release commit and canonical Phase/checklist/journal closure are complete;71.7 history and unrelated changes are preserved.

Stop after exactly71.8. External source/shared-skill changes, successor Phases,
public publication, deployment and push remain excluded.

## Final closure — 2026-10-13

Current closure basis supersedes earlier checkpoint/Step paragraphs that describe
their pending gates when recorded. All three declared Steps have independent
acceptance and exact native local commits: P718-01 `1581c8d389dd036cf1717533d69f62c7e28a0114`,
P718-02 `3befd92f53c0bc6919e1e5be2cc52b266e3e0279`, and
P718-03 `0bf393a5044e87004e7c1171daccf555e56af352`.

The sole full Phase review `p718-full-phase-review17@1` passed in original PLAN
epoch1. Final whole validation first exposed two incomplete Storyboard test
fixtures; the single closure repair added the three prescribed quality/audio/
origin fields to that shared fixture, preserving production code and all
expectations. Its35-specification suite passed; independent focused review
`p718-phase-fixture-focused-result20@1` accepted the repair in the original
full-review chain at ledger12, with zero blockers. Repair count1 and consecutive
non-convergence0 are retained; no second full review was performed.

Final standalone Cozy `sbt --batch test` passed2457 specifications in194 suites,
zero failed/aborted,31 canceled, zero ignored/pending. The31 actual cancellations
remain recorded; no task filters or settings were supplied. Actual SBT/wrapper
exit0 and shared-lock release were verified from the original log/summary.
Separate `cozyExportRuntimeClasspath` succeeded with exit0 and lock release;
all480 entries exist, include compiled Cozy classes and retain `0.3.3-SNAPSHOT`.
The actual final gate receipts are `p718-final-full-test-admission21` and
`p718-final-classpath-admission21` in the ignored Phase workflow records.

Current original JA/EN listening,17-case direct/final/override observations,
34 successful Cozy commands,561 technical checks and408 original visual
observations retain their accepted measured scope. Warm practice medians remain
32.5% shorter on mixed and38.5% on Docker, with cold excluded and all samples/
overhead limits retained. Gap14 was independently reassessed by Step15 and the
sole full review: surviving original evidence supports current product
acceptance, while both missing original API objects stay missing and the old
runner completion stays INCOMPLETE. No evidence reconstruction or hearing
substitution is claimed.

Accepted nonblocking records are persisted exactly in the
[Hygiene journal](../journal/2026/10/2026-10-12-phase-71.8-hygiene-follow-up.md) and
[Development Candidate journal](../journal/2026/10/2026-10-12-phase-71.8-development-candidate-follow-up.md).
Unused template imports and generic authored-lead scheduling remain separate
follow-ups; no such maintenance or scheduling change is included.

The CLOSED declarations are final release projections, effective only after
the distinct local commit carrying
`Phase-Closure-Binding: p718-phase-release-20261013-01@1` succeeds and its native
closure receipt is verified. That commit includes the final Phase/checklist,
accepted journals and the repaired integration specification. The shared
`docs/phase/README.md` and `docs/strategy/cozy-development-strategy.md` projections
are explicitly preserved/deferred because concurrent Phase81/Phase76 planning
and its complete related management data remain outside this Phase. Canonical
Phase/checklist/journals own this closure. Preserve all unrelated dirty work,
original bases, Step/review/repair/refusal history and71.7 acceptance. Stop after
exactly71.8; no successor execution, external/shared-skill change, push,
publication or deployment is included.
