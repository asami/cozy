# Joint video and audio quality profiles

Status: PHASE-71.8 / P718-01 accepted and locally committed, 2026-10-12.
Picture selection and compatibility semantics are frozen.
Audio defaults below have current JA/EN listening acceptance, recorded in
[Phase 71.8](../phase/phase-71.8.md#p718-01-acceptance-evidence).
Executable contract:
[CozyVideoQualityProfileSpec](../../src/test/scala/cozy/video/CozyVideoQualityProfileSpec.scala).

## Selection and migration

Project-root `renderer.qualityProfile` accepts exactly `lightweight`, `standard`,
`quality`, or `high-quality`. Case changes, surrounding whitespace and other
spellings are invalid. Omission, including an absent root renderer, selects new
`standard`. Practice `lightweight` requires explicit selection.

| Canonical profile | fps | width × height | CRF | x264 preset | AAC bitrate | Hardware video bitrate |
| --- | ---: | --- | ---: | --- | --- | --- |
| lightweight | 12 | 854 × 480 | 36 | superfast | 96k | 1M |
| standard | 18 | 1280 × 720 | 32 | omitted | 128k | 2M |
| quality | 30 | 1280 × 720 | 23 | omitted | 192k | 6M |
| high-quality | 30 | 1920 × 1080 | 18 | omitted | 320k | 12M |

`renderer.policy` retains exactly its original three tokens and meanings:

| Authored legacy policy | Canonical tier | Historical picture | Legacy-only audio |
| --- | --- | --- | --- |
| lightweight | standard | 18 fps, 1280 × 720, CRF32 | 320k |
| standard | quality | 30 fps, 1280 × 720, CRF23 | 320k |
| quality | high-quality | 30 fps, 1920 × 1080, CRF18 | 320k |

Existing descriptors can retain `policy` for their old intent. To adopt joint
profile audio, replace it with the mapped canonical tier in `qualityProfile`.
Legacy `policy: lightweight` never means new practice lightweight. A valid
canonical selection wins a valid legacy selection. Every authored field
validates even if shadowed; invalid legacy intent cannot be hidden by canonical.

After selection, each authored `fps`, `width`, `height`, `crf`, `x264Preset`
and `audioBitrate` overrides only its own field. Existing picture validation
stays unchanged: fps/width/height are positive integers, CRF is an integer ≥ 0,
and presets are superfast, veryfast, faster, fast, medium, slow, slower,
veryslow and placebo. Historical optional picture null semantics stay unchanged.
Explicit canonical null is invalid.

Presentation `profile`, `effectProfile`, renderer `strategy`/`engine` and
operating tool/encoder settings are separate. Root renderer supplies encoding;
part renderer retains presentation-summary meaning without new part precedence.

## Audio override

`renderer.audioBitrate` accepts a trimmed positive decimal integer string
matching `[1-9][0-9]*(?:[kM])?` or a positive integral JSON number in bps.
`k` scales by 1000; `M` scales by 1000000. The scaled number must fit positive
signed 64-bit. Valid strings retain trimmed suffix form; integral JSON becomes
integer text for one argv element. No shell parsing or I/O occurs.

Explicit null, blank, zero, negative, fractional, wrong JSON types, unsupported
suffixes and overflow fail as `Decoder.Result`/`Either` values. There is no
arbitrary audio bitrate cap and no new codec, channel or sample-rate setting.
Explicit audio replaces historical 320k independently of picture.

## Resolved contract and encoder

New resolution sets canonical profile, audio and all seven origins. Keys are
`qualityProfile`, `fps`, `width`, `height`, `crf`, `x264Preset`, `audioBitrate`.
Selected/inherited fields use `quality-profile`, `legacy-policy` or `default`
for canonical, legacy-only or omitted selection. Individual overrides use
`explicit`. Legacy-only audio320 uses `legacy-policy`.

The historical `VideoEncodingPolicy` carrier keeps old meanings: canonical
standard and lightweight use old Lightweight, quality uses Standard, and
high-quality uses Quality. Existing generated `encodingPolicy` retains these
meanings; P718-02 appends canonical/audio observations. Original nine-field
`VideoRenderer` and six-field `ResolvedEncodingSettings` positional constructors
remain source-compatible through trailing defaults. Legacy internal resolved
construction falls back from old policy to canonical tier and AAC320.
No binary compatibility is claimed.

Hardware uses canonical 1M/2M/6M/12M defaults; old props without `qualityProfile`
retain 2M/6M/12M from `encodingPolicy`. A present canonical prop is authoritative
and invalid canonical props fail even with explicit operating bitrate.
Operating video bitrate overrides hardware default with its own origin.
Hardware requires host FFmpeg, disables software fallback and has no
CRF/preset/tune. Software retains CRF/preset and simple-java2d stillimage tune
and rejects operating video bitrate.

## Route and acceptance boundaries

P718-02 is IN_PROGRESS. Its executable route contract is
[CozyVideoQualityRouteSpec](../../src/test/scala/cozy/video/CozyVideoQualityRouteSpec.scala),
with Storyboard roundtrip contracts in CozyVideoStoryboardReviewSpec and
CozyVideoStoryboardBuildSpec. Native/mixed encoding, Docker Remotion mux and
final assembly consume resolved audio. Propagation preserves lossless intermediate
inputs, one final AAC encode, 48 kHz stereo, rational sample ending and complete
scene/end-card timing. Final assembly must not restore fixed320k after a
lower-bitrate direct render.

P718-02 explicitly supersedes the undated fixed320k stitcher and final-assembly
bitrate statements in the Storyboard
[MP4 audio presentation and final assembly contract](video-storyboard.md#mp4-audio-presentation-and-final-assembly).
For resolved joint selections, direct encoding, Remotion stitcher and final
assembly use the selected `audioBitrate`. Historical320k remains only for the
legacy-policy selection, missing legacy props and old internal helper
construction compatibility cases specified here. The lossless PCM, single
final AAC encode, cutoff, channels, sample rate and ending contracts continue
to apply; dated predecessor history remains historical evidence.

### Generated joint observations and legacy inputs

Project, part, RDF and Storyboard mode `encoding` objects retain the six keys
`policy`, `fps`, `width`, `height`, `crf`, `x264Preset` and append
`qualityProfile`, normalized `audioBitrate`, and lexically sorted `origins`.
Remotion props and the review manifest renderer instead retain `encodingPolicy`
and use `encodingOrigins`. Simple-java2d props append canonical profile, audio
and origins without changing their existing still-image dimensions.
External encoder diagnostics record these selected fields alongside actual argv
and exit; a Docker renderMedia invocation does not acquire invented FFmpeg argv.

Inspect and build dry-run show `qualityProfile`, `encodingAudioBitrate` and
sorted `encodingOrigin.<field>: <source>` lines. RDF adds `qualityProfile`,
`audioBitrate` and `encodingOriginQualityProfile`, `encodingOriginFps`,
`encodingOriginWidth`, `encodingOriginHeight`, `encodingOriginCrf`,
`encodingOriginX264Preset`, `encodingOriginAudioBitrate` predicates while
retaining historical policy triples. These are resolved encoding intent, not
measured bitrates or proof of native execution.

Native and generated Node audio adapters validate every present canonical token
even with an explicit audio override. Canonical props require audioBitrate;
supplied audio validates even without canonical props. Both fields absent means
historical320k. Explicit null or malformed audio never means omission. Node
string parsing uses positive signed64 bounds with BigInt and accepts positive
integral safe JSON numbers, rejecting numbers that JavaScript cannot represent
exactly. Selection is one argv value without shell parsing.

Review inputs retain six-field error precedence. Presence of any joint field
requires all three fields to equal current canonical/audio/origin intent.
Missing-all joint fields is compatible only with original policy selection and
legacy-policy320k audio, or old internal construction with empty origins and
320k. Default/canonical plans and explicit audio overrides require current joint
evidence, including explicit320k. Publication metadata also validates all three
when any is supplied: exact canonical token, already normalized audio string,
and exactly the seven known origin keys with default/legacy-policy/quality-profile/
explicit values. Old six-field publication records pass through without
fabricated audio/origins or measured-media claims.

Authored Document Project and Storyboard renderer records preserve optional
qualityProfile/audioBitrate only when supplied, without new null placeholders.
Storyboard review still requires all nine historical renderer fields. The two
new fields are optional but every supplied value is retained and validated;
invalid optional fields cannot disappear as arbitrary extra metadata.

Multi-part narrated assembly independently decodes each input to PCM, allocates
cumulative rational sample floors, copies video and concatenates PCM before one
selected-rate AAC encode with cutoff18000, stereo48k and movie_timescale48000.
Single-part and video-only paths retain stream copy. Old package helper calls
retain default320k. Direct frame encoding retains all N frames, full sample
trim, video timescale and final card; simple-java2d retains its existing
shortest/timing ownership with explicit selected audio, stereo48k and cutoff.
Implementation and local fixture execution do not establish Step acceptance;
parent validation and independent review remain outstanding.

The test-only managed helper
[compare-video-quality-audio.py](../../scripts/test/compare-video-quality-audio.py)
independently encodes retained lossless JA/EN WAV at 320k baseline and
96k/128k/192k, with selected explicit FFmpeg argv, stereo48k and cutoff18000.
It retains listening files, full-decode exits, measured bytes, source/output
duration and source-language records in JSON; it never reports listening PASS.
The parent selects inputs/executable and owns execution and attributable human
listening before P718-01 acceptance/commit. Audio-default adjustments require new
frozen bounded edits and refreshed affected validation.

P718-03 owns actual comparable lightweight/standard render, encode and total
time measurements on the same inputs, route and encoder, plus useful
storyboard/layout/narration and direct/final/override observations. Smaller
pixels, higher CRF, successful decode and passing specs establish neither
listening quality nor practice speed. Final validation and release remain
outstanding in the [Phase checklist](../phase/phase-71.8-checklist.md).
