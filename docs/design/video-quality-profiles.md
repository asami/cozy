# Joint quality resolution ownership

Status: PHASE-71.8 / P718-01 accepted and locally committed, 2026-10-12.
Frozen decisions accompany the
[external contract](../spec/video-quality-profiles.md) and
[executable specification](../../src/test/scala/cozy/video/CozyVideoQualityProfileSpec.scala).
Audio defaults have current JA/EN listening acceptance; the actual evidence is
recorded in [Phase 71.8](../phase/phase-71.8.md#p718-01-acceptance-evidence).

`CozyVideoQuality` is a stable package-private object owning four closed Profile
alternatives, pure joint resolution and the renderer decoder. It consumes
existing stable data types in `CozyVideoModel`; it does not move model types
into an instance-bearing trait. `VideoRenderer` companion constants and
decode/resolve entry points remain adapters. This cohesive extraction avoids
growing Model while retaining its prior recorded size follow-up. Broad
model/alias and accumulated-spec splits remain separate maintenance.

Canonical selection and historical carrier are distinct because changing old
policy meanings would silently alter authored descriptors. Original policies
map to their shifted canonical tiers. Trailing defaulted canonical/audio fields
preserve positional constructors. New resolution always records canonical,
profile/legacy/explicit audio and all field origins. Canonical fallback from
historical policy is reserved for old internal resolved construction.

Decoding validates legacy selection before canonical precedence and validates
every individual field. Bitrate parsing returns values and uses BigInt for the
scaled signed-64-bit boundary without tool I/O. Operating video bitrate remains
independent of audio. Encoder uses canonical defaults when available and old
props otherwise, retaining host and no-fallback constraints.

Consumer responsibilities stay fixed: Planning selects root encoding; Publisher
and StoryboardReview roundtrip descriptor fields in P718-02; RenderTemplates
carries canonical props and mux audio; FrameEncoding changes audio selection
while preserving frame/workspace/ending checks; BuildReplay consumes the same
audio without another lossy intermediate; Presentation, RDF, ReviewEvidence
and StoryboardBuild append accurate observations. Part renderer presentation
summaries retain their role.

P718-02 uses `CozyVideoQuality.encodingJson` as the single pure joint projection.
BuildReplay keeps its package bridge; Storyboard mode, RDF, part and review
records consume the projector, using `encodingPolicy`/`encodingOrigins` for the
review renderer. Native props audio admission is an Either-valued adapter of the
accepted decoder and bitrate vocabulary. Generated render.mjs performs the
equivalent canonical/bitrate checks before Remotion callback execution. It
changes only stitcher AAC bitrate; pre-stitcher PCM, full frames/sample ending
and mux timescales keep their existing ownership.

Normal and Storyboard assembly pass actual root encoding through an appended
defaulted package-helper parameter. The compatibility default is historical320k;
new calls pass resolved audio explicitly. Independent input decode, cumulative
rational allocations, lossless concat and sole final AAC remain in BuildReplay.
No new part precedence, video/audio coupling or external responsibility is added.

Observation compatibility is additive and bounded: six-field checks remain
first, and supplied joint fields require complete accurate intent. Missing-all
review evidence is limited to legacy-only320 or old internal320 construction;
old publication metadata remains old metadata. Publisher and Storyboard
serialization append authored fields only when supplied. Known optional fields
are validated rather than discarded by the old nine-key filter. Selected
intent and actual external argv remain distinct from measured-media evidence.
The [route specification](../../src/test/scala/cozy/video/CozyVideoQualityRouteSpec.scala)
executes generated JavaScript using local test modules; this is callback evidence,
not Docker or media acceptance. P718-02 validation and independent focused
review PASS are accepted; its exact local Step commit is
`3befd92f53c0bc6919e1e5be2cc52b266e3e0279` and the Step is CLOSED on2026-10-12.

P718-01 implements accepted profile audio and the test-only comparison helper.
Current listening and independent Step review have passed; the exact local
Step commit succeeded. P718-02 propagates audio routes.
P718-03 measures comparable practice speed and usefulness. Neither the preset
table nor successful decode proves acceptance. Lossless inputs, one final AAC
encode, stereo48k, rational ending and complete end card remain shared media
boundaries. Public publication and external source changes are outside scope.

P718-03 measurement tooling owns task-private fixture preparation, observation,
collection, technical inspection and comparison under
`target/phase718/quality-media03` for the selected changed-producer matrix;
root01 and the failed root02 attempt remain retained historical evidence. It preserves producer/encoder/final-assembly
responsibilities and accepted preset/precedence semantics. The direct stage
uses a single part; final assembly consumes that actual media twice as ordered
dialogue/dialogue-repeat inputs, independently decoded to PCM before sole final
AAC. Final observations consume the actual final.v2 manifest and its declared
part-artifact.v2 records; they do not require a fictitious second producer.
Authored scene lead-in remains part of allocation validation; correspondence
must follow the actual selected template's scheduling. Explicit
`rendererTemplate=null` generic audio starts at openingFrames plus accumulated
scene start, whereas `cozy-character-dialogue-v1` and
`cozy-character-web-demo-v1` add leadInFrames to openingFrames plus startFrame.
The corrected inspector requires contiguous positive scene allocations and
nonnegative authored lead/source duration within the final-card boundary,
reports whether lead is applied, and checks all nine original WAVs/full tails
in both copies. Missing/unknown templates fail without alternate-origin search
or weaker thresholds. Production scheduling is unchanged: the generic
metadata/actual-placement discrepancy already existed at Phase base83ee0f2.
`DEV-P718-03-GENERIC-AUDIO-TIMING-01` retains that behavior/design question
outside this measurement boundary, pending independent scrutiny. Measuring
existing placement neither implements generic lead silence nor waives current
preservation, useful-layout or acceptance requirements.

Observers forward actual options and exits and keep stage clocks in their own
domains. Docker renderMedia progress/render/encode windows overlap; summing
them or subtracting unrelated clocks would manufacture timing. Native command
duration comes from matched actual commandExecution history supplied by the
parent. Measurement helpers cannot invoke Cozy or grant technical, human,
Step or Phase acceptance. Root01 excluded cold warm-ups/clipping07 and root02
observer-selection failure08 remain historical. Current checkpoint13 records
all17 selected root03 technical/visual observations,34 original successful Cozy
commands,34 full decodes and408 individually parent-viewed PNGs with readable,
unclipped original text and labels. Warm whole-pair medians are32.5% shorter for
lightweight on mixed and38.5% shorter on ordinary Docker. Layout04 retains5+163
Scala successes and one explicit opt-in Docker cancellation; helper-tests06
passed14 groups in1.034s after the three coherent literal selector edits.
These observations leave16 complete case-runs and gap14 on docker-standard/warm02:
first inspection exit0 is retained, but original initial-exec/renderer API
objects are missing and runner completion is INCOMPLETE. Fresh independent
Step reassessment and Step/Phase acceptance remain pending.
Inspection uses an explicitly selected new owned directory
and retains the original failed attempt; summary consumes exact parent-selected
inspection paths, rejects duplicate case/runs and cannot choose latest or
silently discard failures. This evidence selection adds no producer change,
retry, source snapshot, hash or size-based sameness certificate. The
[journal](../journal/2026/10/2026-10-12-phase-71.8-quality-media-acceptance.md)
records current measured speed/usefulness, timing limits and gap14 alongside
the preserved cold measurements and failure history, without Step acceptance.


## Generic SceneCard layout ownership

The settled correction belongs solely to the generic Root generated by
`CozyVideoRenderTemplates`. A package-private String
`_remotion_scene_card_layout_js` precedes `_remotion_root_tsx` and defines one
plain-JavaScript `_scene_card_layout` helper with numeric defaulted destructured
width1280/height720. Generated Root concatenates this exact helper after imports
and before types. SceneCard becomes a block-body component, reads actual
width/height using `useVideoConfig`, and uses all five helper results.

The helper returns padding, fontSize, labelFontSize, labelBottom and labelRight
from scale `min(1,width/1280,height/720)` and reference80/56/24/48/64px.
It shrinks these together below reference and caps them at reference above it;
854×480 uses2/3 and960×540 uses3/4. This is composition geometry, independent
of profile selection, fps or audio. It adds no generated module, dependency,
workspace writer, public API, schema, type or auxiliary architecture.
Generic Audio remains at scene-local0 and generic Sequence logic is preserved.
Strings, line-height1.25, fonts/colors/alignment/IDs, scenes/timing/effects,
assets/credits and encoding remain intact; no crop, hidden overflow, truncation,
fixture shortening or character/web-demo/simple-java2d change is permitted.

The delivered package-level companion helpers in CozyVideoSceneLayoutSpec execute
the identical JavaScript String as a Node.mjs module, with numeric behavioral
specifications and ScalaCheck properties; source snippets or regex extraction
cannot substitute for execution. Actual generated Root wiring and visible
nine-scene text have current observations in the17-run,34-CLI root03 matrix,
with408 individually viewed PNGs across34 direct/final movies. Three existing
literal selectors moved coherently to root03: helper TARGET, Node metadata host
projectRoot suffix and host FFmpeg canonical project suffix. All other observer guards,
forwarding, lifecycle and measurement algorithms remain intact. Existing14
helper specifications remain unchanged; their actual06 refresh passed14 groups
in1.034s. There is no new root API, retry or architecture.
Delivered layout/spec and5+163 Scala receipts remain reusable at their original
scope without an unconditional SBT refresh. Root03 media/visual/speed observations
are recorded; gap14 independent reassessment and Step/Phase acceptance remain
pending. Root01 movies/failures and root02's
prepared project/terminal failure are preserved without credit toward root03.
