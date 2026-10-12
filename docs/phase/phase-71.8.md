# Phase 71.8: Unified Video and Audio Quality Profiles

status=in-progress
depends_on=phase-71.7.md
execution_priority=after_phase_71.7_closure
validation_ownership=standalone

Status: IN_PROGRESS; P718-01 CLOSED; P718-02 CLOSED; P718-03 IN_PROGRESS
Planned at: 2026-10-11
Started at: 2026-10-12
Primary owner: Cozy
Predecessor: [Phase 71.7](phase-71.7.md).
Completion ledger: [Phase 71.8 Checklist](phase-71.8-checklist.md).

## Goal and boundary

Let an operator select a video quality profile that resolves both picture and
audio encoding settings, with individual overrides when needed. Rebase the
quality ladder on the current lightweight picture settings: current lightweight
becomes new `standard`, current standard becomes new `quality`, and current
quality moves to the upper name `high-quality`. Add a new `lightweight`
profile for practice drafts, with lower picture quality and measurably faster
generation than the new standard. Keep quality selection distinct from the
existing explanation/dialogue presentation profiles and tool/encoder choice.

The historical policy carrier for the default picture is `lightweight`. The user
explicitly selected new `standard` as the Phase71.8 default, retaining the
current default picture settings:18fps,1280×720,CRF32. The new practice
`lightweight` profile requires explicit selection. P718-02 propagates selected AAC bitrate;
joint-profile audio values now have current JA/EN listening acceptance. Specify migration of
existing named selections and overrides before implementation so their intended
quality is not silently changed by the shift in names.

This is new work after 71.7, not a transfer of unfinished 71.7 acceptance.
Phase 71.7 retains its current Goal, media/integration/benchmark obligations,
aggregate validation ownership, review history and release boundary. The
original plan started no execution and performed no commit. This Phase is now
in progress under `p718-phase-authority-20261012-01@1`; predecessor history and
its acceptance scope remain intact.

## P718-01: Profile contract and executable specifications

Status: CLOSED on 2026-10-12; implementation, validation, current listening,
independent Step review and exact local commit complete. Frozen contract and implementation are bound to
`p718-01-qcontract-implementation01@1`. See
[quality specification](../spec/video-quality-profiles.md),
[resolution design](../design/video-quality-profiles.md) and
[CozyVideoQualityProfileSpec](../../src/test/scala/cozy/video/CozyVideoQualityProfileSpec.scala).
Canonical lightweight/standard/quality/high-quality resolve
12fps480pCRF36/superfast/96k/1M,
18fps720pCRF32/128k/2M,
30fps720pCRF23/192k/6M and
30fps1080pCRF18/320k/12M respectively. Audio defaults have current JA/EN
lossless-source comparison and attributable human listening acceptance.
Explicit legacy policy keeps its old picture and320k intent; canonical wins
valid legacy, and independently validated field overrides win their own fields.
P718-02 still owns audio route propagation and generated observations.

Define the joint profile, audio bitrate customization, existing picture
overrides, configuration precedence and observable effective settings.
Specify the shifted ladder, new standard default, practice-profile behavior,
omitted settings and migration of legacy named selections. Retain 48 kHz stereo
and the current sample-based audio ending contract for this bounded change.
Confirm candidate audio preset values through listening before treating them
as accepted defaults; the [planning note](../notes/video-quality-profiles.md)
contains the initial candidates, not a normative encoding contract.

Promote the selected behavior into video specifications/design and Given /
When / Then executable specifications, including override and invalid-setting
behavior. Preserve supported software/hardware encoder selection.

### P718-01 acceptance evidence

On 2026-10-12, the current lossless-source JA/EN comparison completed eight
independent AAC encodes and eight full decodes successfully. Source WAVs were
PCM16,24kHz mono; comparison outputs were AAC-LC,48kHz stereo with cutoff18000.
Each rate was encoded from its original lossless source, with retained actual
argv, exits, duration and bytes at `target/phase718/audio-comparison01/`.

| Language | Source duration (s) | 320k bytes | 96k bytes | 128k bytes | 192k bytes |
| --- | ---: | ---: | ---: | ---: | ---: |
| JA | 20.48 | 567851 | 249962 | 330696 | 497834 |
| EN | 18.111625 | 521636 | 221999 | 294811 | 440443 |

The current user confirmed: 「8本とも自然に聞こえた。71.8を再開して」.
This accepts96k/128k/192k against320k for these selected speech samples and
this native AAC encoder; it is not a universal perceptual-equivalence claim,
Docker execution result or practice-speed measurement.

The registered serialized runner passed15/15 focused quality specifications
and165/165 accumulator specifications across the five declared suites, with
SBT/wrapper exit0 and terminal lock release. Independent protected-focused
Step review `p718-01-independent-step-review01@1` returned PASS with no blockers;
typed ledger `p718-review-ledger-20261012-01@2` was saved and read back. Existing
Publisher/specification size debt remains in HYG-P71.2-007/HYG-P71.2-002, and
the prior Model size follow-up remains open; no maintenance split is admitted.
P718-01's exact local commit succeeded as
`1581c8d389dd036cf1717533d69f62c7e28a0114`, containing the ten owned files.
This completed Step does not close the Phase. P718-02, P718-03, full Phase
review and final validation/release remain outstanding.

## P718-02: Shared resolution across encoding routes

Status: CLOSED on 2026-10-12; implementation, proportional validation,
independent acceptance and exact local Step commit complete under
`p718-02-routes-implementation01@1`.
The [route specification](../../src/test/scala/cozy/video/CozyVideoQualityRouteSpec.scala)
covers native/mixed commands, executed generated Node mux callbacks, normal and
Storyboard assembly, joint observations, legacy review evidence and publication
metadata admission. Storyboard review/build specifications cover optional
authored renderer serialization and strict supplied-field validation.
The registered serialized runner passed18/18 route specifications and228/228
accumulator specifications across ten suites, with SBT/wrapper exit0 and
terminal lock release. Independent Step review found two bounded issues:
legacy Storyboard final-manifest admission and an undated fixed320k statement.
Their three-path repair and same-cycle fixture correction were validated;
focused independent review `p718-02-focused-step-review-fix04-01@1` returned PASS,
resolving both issues with no new blocker. Typed ledger
`p718-review-ledger-20261012-01@6` was saved and read back.
All four repair cycles and the earlier failed results remain recorded.
The exact local Step commit succeeded as
`3befd92f53c0bc6919e1e5be2cc52b266e3e0279`, containing the22 owned files.
The parent read back the terminal native receipt, actual HEAD, ordered parent
and exact committed path set; the25 unrelated dirty paths remained outside
the commit. This establishes P718-02 closure, not Phase closure.
Generated Node callbacks and injected media runners establish the affected
route behavior, not actual Docker media, perceptual quality or practice speed.
P718-03 and the standalone Phase review/validation/release duties remain open.

Make the native/mixed frame encoder, Docker Remotion audio mux and final
assembly consume the same resolved audio quality settings. The final build
must not silently restore a fixed320k value after a lower-bitrate direct render.
Expose selected profile, effective picture/audio settings and their origins in
`video inspect` and relevant generated records. Preserve narration timing,
lossless intermediate audio, one final AAC encode and the complete end card.

## P718-03: Quality, size and regression acceptance

Status: IN_PROGRESS on2026-10-12. Checkpoint13 records all17 selected root03
technical/visual observations:34 successful original Cozy commands,17 reports/
561 actual checks,34 fully decoded movies and408 individually parent-viewed
PNGs. Original nine texts/labels/opening/summary/credits are readable and
unclipped in selected frames. All movies show H264/AAC stereo48k, rational ends
within one sample, nine/eighteen original WAV full≥0.80/tail≥0.60 matches and
complete two-second end-card frames. Root01 cold measurements/clipping07 and
wrong-origin05/snapshot-collision06 remain preserved history.

The settled generic lower-resolution SceneCard contract and ownership are in
[spec](../spec/video-quality-profiles.md#generic-scenecard-lower-resolution-layout)
and [design](../design/video-quality-profiles.md#generic-scenecard-layout-ownership).
Layout04 product/spec delivery completed; CozyVideoSceneLayoutSpec passed5
specifications and the five affected suites passed163, with one explicit opt-in
Docker cancellation at layout04 scope. Earlier helper-tests05 passed14 scenarios
in1.030s. Root02's first
mixed-standard CLI then failed during observer tool checking (exit1,8.988s),
before any new movie. Parent diagnosis08 identifies both observers still
selecting root01 while helper TARGET selected root02.
The complete matrix at `target/phase718/quality-media03/` comprises both routes,
cold plus three paired warm runs and one mixed override cold pair. Three coherent
literal selectors are delivered; helper-tests06 refreshed all14 groups in1.034s.
Original CLI/Node/Docker final exercised both observers at root03. Other guards,
forwarding/measurement behavior and helper specifications remain unchanged.
Mixed lightweight/standard warm whole-pair medians are99.736/147.785s (32.5%
shorter); Docker80.229/130.418s (38.5% shorter). All three pairs, including the
longer third Docker lightweight run, remain recorded; cold is excluded. Totals
include native lifecycle/tool/observer overhead, exclude agent queue/idle and
make no process-hot/system-cold or uninterrupted wall-clock claim; overlapping
Docker windows are not added or called pure encode.
The explicit lightweight override observed960×540@15fps/CRF28/veryfast/AAC192k,
six explicit field origins, quality-profile selection and4023/8046 frames.
Direct144.815s/final12.423s total157.238s is cold and excluded from speed.
Independent final-input PCM decodes precede one final AAC; the repeat input is
copied rather than independently produced.
There are16 complete case-runs plus `P718-03-RUNTIME-INSPECTION-REPLAY-14` on
docker-standard/warm02. First inspection exit0/33 checks/24 viewed PNGs and
original app history/first terminal poll are retained; the erroneous duplicate
exited1 at the existing-directory guard before probing. Original initial-exec
and renderer API objects remain missing and runner completion is INCOMPLETE.
Fresh independent Step reassessment including gap14, exact local Step commit
and Phase review/validation/release gates remain pending. Aggregate observations
do not discharge gap14 or establish Step acceptance. Root01/root02 evidence and
failure chronology are preserved without credit as root03 outputs.
The [quality/media journal](../journal/2026/10/2026-10-12-phase-71.8-quality-media-acceptance.md)
records current observations, all samples/bytes and retained failure history.
`DEV-P718-03-GENERIC-AUDIO-TIMING-01` remains untouched separate follow-up,
pending independent scrutiny; all nine original WAVs and current generic
scene-local0 audio/positive lead metadata remain exposed. No silence
implementation or source/tail/usefulness waiver is claimed. P718-01/P718-02
closed status, exact local commits and original acceptance scopes remain intact.

### P718-03 independent acceptance checkpoint

On2026-10-12, fresh independent Step review
`p718-03-independent-step-review15@1` returned PASS with zero current blockers
over all12 owned paths, six whole target programs and affected integration
edges. Typed ledger `p718-review-ledger-20261012-01@9` was saved and read back.
The accepted P718-01 JA/EN hearing and size evidence retains its original scope;
current17-case speed, technical ending and useful-layout observations are
accepted at this Step's measured scope. The preceding checkpoint13/14 paragraphs
retain their earlier pending state as history.

The reviewer independently assessed gap14 from original native history,
terminal exit0,33 inspection records and original PNG evidence. This supports
current product-evidence acceptance; both missing original API objects remain
missing and old runner completion remains INCOMPLETE. No reconstruction,
retroactive execution approval or rerun occurred. Generic lead scheduling is
confirmed as nonblocking `DEV-P718-03-GENERIC-AUDIO-TIMING-01`; existing unused
imports are `HYG-P718-03-TEMPLATE-IMPORTS-01`. They remain separate follow-ups.
The local Step commit, sole Phase full review, final full `test`, runtime
classpath export and distinct release commit/closure are still pending.

Compare selected JA/EN narration from retained lossless inputs at the baseline
and candidate bitrates. Record actual sizes and attributable listening results;
do not infer perceptual equivalence from a bitrate or successful decode.
Measure new lightweight versus new standard on the same representative inputs,
tool route and encoder, recording render/encode/total time and image quality.
Accept the practice profile only when actual generation is faster and its
storyboard, text/layout checks and narration remain useful for practice review;
a higher CRF or smaller file alone does not establish faster generation.
Verify representative direct/final output, effective overrides and common
media endings on the affected routes. Reuse relevant existing evidence and
run proportional focused specifications before independent review and commits.
Declare and complete this Phase's final validation/release obligations at entry;
do not claim 71.7's earlier suite validates later71.8 implementation changes.

## References

- [Joint quality specification](../spec/video-quality-profiles.md)
- [Joint resolution design](../design/video-quality-profiles.md)
- [Current JA/EN comparison helper](../../scripts/test/compare-video-quality-audio.py)
- [Request and baseline observation](../journal/2026/10/2026-10-11-phase-71.8-video-quality-profile-plan.md)
- [Candidate profile values and compatibility questions](../notes/video-quality-profiles.md)
- [Video tool execution specification](../spec/video-tool-execution.md)
- [Video storyboard specification](../spec/video-storyboard.md)

## Validation and release duties

Parent-selected representative validation is `testOnly cozy.video.CozyVideoQualityProfileSpec`.
The focused accumulator is `testOnly cozy.video.CozyVideoSpec cozy.video.CozyVideoHardwareEncodingSpec cozy.video.CozyVideoProfileRenderSpec cozy.video.CozyVideoScaffoldSpec cozy.video.CozyVideoCompatibilitySpec`.
P718-02's representative is `testOnly cozy.video.CozyVideoQualityRouteSpec`.
Its accumulator is `testOnly cozy.video.CozyVideoQualityProfileSpec cozy.video.CozyVideoSpec cozy.video.CozyVideoMixedRenderSpec cozy.video.CozyVideoHardwareEncodingSpec cozy.video.CozyVideoAssemblyAudioSpec cozy.video.CozyVideoStoryboardReviewSpec cozy.video.CozyVideoStoryboardBuildSpec cozy.video.CozyVideoProfileRenderSpec cozy.video.CozyVideoScaffoldSpec cozy.video.CozyVideoCompatibilitySpec`.
P718-03 additionally selects representative `testOnly cozy.video.CozyVideoSceneLayoutSpec`
and affected accumulator `testOnly cozy.video.CozyVideoSpec cozy.video.CozyVideoQualityRouteSpec cozy.video.CozyVideoMixedRenderSpec cozy.video.CozyVideoProfileRenderSpec cozy.video.CozyVideoMediaEndingSpec`.
These completed with5 and163 successes respectively, plus one explicit opt-in
Docker cancellation; receipts remain reusable at their original layout04 scope.
Helper-tests05 completed14 scenarios in1.030s before the root02 CLI failure;
helper-tests06 refreshed14 groups in1.034s after the delivered three coherent
root03 literal selector changes. The actual17-run matrix used registered
Cozy/runtime runners and retains gap14's incomplete runner completion.
P718-02's accepted18 route and228 accumulator results may be reused for
unchanged Scala/config at its committed boundary; causal source/generated or
dependency changes require affected refresh. Changed RenderTemplates received
the selected five-suite refresh; the three test-only root selectors introduce
no Scala/config/dependency change or unconditional SBT requirement. Unchanged
profile/assembly/hardware/Storyboard pure-logic receipts retain original scope. Current runtime uses
`target/cozy.d/runtime-classpath.txt` through exact `--runtime-dev-dir`; actual
CLI/props must prove current behavior. Helper tests and prior Scala receipts
cannot substitute for native measurements, useful-layout review, independent
P718-03 review or its exact local Step commit.
The route suite requires Node from PATH or the `cozy.video.quality.node` property;
missing Node fails explicitly rather than cancelling required specifications.
All SBT runs use the registered runner and shared serial wrapper with scoped
permission and terminal lock-release evidence. P718-01 acceptance additionally
requires current retained lossless JA/EN comparison, full decode, actual bytes
and attributable human listening; prior71.7 hearing is not substituted.

Each Step requires proportional validation, independent acceptance review and
its exact local commit. One comprehensive Phase review is permitted per PLAN
epoch. Standalone final-only full Cozy `test` and `cozyExportRuntimeClasspath`
must pass on the release candidate before the distinct bound local release
commit and canonical checklist/journal closure. Practice speed and usefulness,
direct/final/override observations and media ending duties remain P718-03.
No acceptance, listening quality, measured speed or Phase closure is claimed
by implementation alone. Stop after71.8; successor work, shared-skill redesign,
external source edits and public publication/deployment/push are excluded.
