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
not Docker or media acceptance. P718-02 validation/review/commit are pending.

P718-01 implements accepted profile audio and the test-only comparison helper.
Current listening and independent Step review have passed; the exact local
Step commit succeeded. P718-02 propagates audio routes.
P718-03 measures comparable practice speed and usefulness. Neither the preset
table nor successful decode proves acceptance. Lossless inputs, one final AAC
encode, stereo48k, rational ending and complete end card remain shared media
boundaries. Public publication and external source changes are outside scope.
