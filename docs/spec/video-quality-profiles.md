# Joint video and audio quality profiles

Status: PHASE-71.8 / P718-01 contract accepted, 2026-10-12; exact local
Step commit pending. Picture selection and compatibility semantics are frozen.
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

P718-01 resolves accepted profile audio; existing routes still use AAC320 until
P718-02 propagates it through native/mixed encoding, Docker Remotion mux,
final assembly and observations. Propagation preserves lossless intermediate
inputs, one final AAC encode, 48 kHz stereo, rational sample ending and complete
scene/end-card timing. Final assembly must not restore fixed320k after a
lower-bitrate direct render.

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
