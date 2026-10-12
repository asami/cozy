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

P718-02 is CLOSED on 2026-10-12 at local Step commit
`3befd92f53c0bc6919e1e5be2cc52b266e3e0279`. Its executable route contract is
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
P718-02 acceptance includes18 passing route specifications,228 passing
accumulator specifications and independent focused review PASS, as recorded in
[Phase 71.8](../phase/phase-71.8.md#p718-02-shared-resolution-across-encoding-routes).
These executable route results do not establish current native media or speed.

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

### P718-03 selected measurement contract

Current checkpoint13 records all17 selected case/runs technically and visually
observed under `target/phase718/quality-media03/`:34 successful original Cozy
commands,17 reports/561 actual probe/decode/extraction calls,34 fully decoded
movies and408 individually parent-viewed PNGs. Original nine texts/labels,
opening/summary/credits are readable and unclipped in the selected12 PNGs/movie;
H264/AAC stereo48k, rational ends within one sample, nine/eighteen original WAV
full≥0.80/tail≥0.60 matches and complete two-second end cards were observed.
Root01 clipping07 and root02 observer-selection failure08 remain historical in
the journal. Three coherent root03 selector edits are delivered; helper-tests06
passed14 Given/When/Then groups in1.034s. Layout04 retains5+163 Scala successes
and one explicit opt-in Docker cancellation at its original scope.
Warm whole-pair medians are lightweight99.736s versus standard147.785s on mixed
(32.5% shorter), and80.229s versus130.418s on Docker (38.5% shorter); cold runs
are excluded. These observations do not establish Step acceptance: there are
16 complete case-runs and gap14 on docker-standard/warm02, whose first inspection
was observed but original initial-exec/renderer API objects remain missing.
Runner completion is INCOMPLETE and fresh independent Step reassessment is
mandatory. The matrix compares
mixed-standard/lightweight using Docker Remotion frames/audio mixing and host
software libx264, and docker-standard/lightweight using ordinary Docker
renderMedia/stitcher and Docker final assembly. A mixed-override case selects
lightweight with explicit fps15,width960,height540,crf28,x264Presetveryfast,
audioBitrate192k. All use the same nine JA scenes, lossless WAVs, assets,
credits, current template and `ghcr.io/asami/textus-toolchain:0.2.1-SNAPSHOT`.
Other canonical tiers retain their accepted P718-01/02 specification coverage.

Preparation removes only root fps/width/height and authored legacy policy from
the selected copied descriptor, sets canonical qualityProfile and preserves
part presentation renderer. A single dialogue part produces the complete
summary/end card. Only after current direct media and props exist does
prepare-final copy that media to dialogue-repeat and write two ordered parts
for `--mode final`. Each next warm prepare restores the single-part descriptor.
The repeated file is an explicit second assembly input, not a second producer.
Assembly independently decodes both inputs to PCM and encodes final AAC once.

Collect current props and all seven origins, real native/libx264 argv and
Docker stitcher `-b:a`; measured AAC average remains distinct from its target.
Require `target/cozy-video/final/manifest.json` with
`cozy.video.final.v2`, mode final/status validated, safe project-relative
output.path, and exactly dialogue/dialogue-repeat in buildSettings.parts and
partArtifacts order. Consume declared safe artifact JSONs with
`cozy.video.storyboard-part-artifact.v2` and current part IDs/outputs.
Retain each run's factual producer reports; hashes and source-body copies are
not evidence requirements.

For each comparable route, retain one active project per profile, one cold
warm-up and three paired warm runs in those same projects/caches. Order is cold
standard/lightweight, warm01 lightweight/standard, warm02 standard/lightweight,
warm03 lightweight/standard. Each run keeps unique outputs/logs; no cache
deletion or process-hot claim. The override needs one actual direct/final pair.
Practice speed requires the three-pair warm median of render-plus-final native
tool wall duration to be lower for lightweight independently on both routes,
with all samples, ratios, bytes, overhead and limitations reported. Missing,
failed, incomplete, one-sided or slower evidence cannot establish acceptance.

Full-decode and probe current direct/final media: h264/AAC, selected dimensions,
fps, stereo48k, actual frame counts (final twice direct totalFrames), and
rational presented video/audio ends within one48k sample. Inspection
selects narration origins from the explicitly emitted `rendererTemplate`:
`null` means generic audio starts at `openingFrames + accumulated scene start`;
`cozy-character-dialogue-v1` and `cozy-character-web-demo-v1` use
`openingFrames + startFrame + leadInFrames`. Missing or unknown templates fail;
inspection must not search alternative origins or reduce waveform thresholds.
Require each scene's startFrame to equal accumulated positive durationFrames,
nonnegative authored leadInFrames, lead-plus-source/audioDurationFrames
allocation, and no narration crossing the final-card boundary. Match all nine
original WAVs in full and at their last energetic tails in both final copies.
Report template, scheduling policy, authored lead and whether it is actually
applied. This measures existing scheduling; it does not assert generic authored
lead silence is implemented. That untouched discrepancy remains
`DEV-P718-03-GENERIC-AUDIO-TIMING-01` for separate design follow-up and
independent scrutiny, without waiving current media/usefulness duties.
Require positive, complete end-card holds and
inspect every hold frame for nonblack content. Opening, dense content,
convergence, summary and ending PNGs support parent layout/usefulness review.
Technical correspondence never grants human hearing or visual acceptance.
The bounded P718-01 eight-file JA/EN listening evidence remains reusable only
at its original scope. The selected protocol, current observations, timing
limitations and gap14 are
recorded in the [P718-03 journal](../journal/2026/10/2026-10-12-phase-71.8-quality-media-acceptance.md).

The private `inspect --output-directory` selector accepts a new safe directory
below the selected task-private `inspection/` root; existing/unsafe destinations
are refused. The corrected helper's14 scenarios and the two old cold technical
inspections are actual evidence at their original root01 scope. Retain the
wrong-origin05 and snapshot-name-collision06 attempts, all old movies, native
records and clipping PNGs, and root02's prepared project and terminal failure.
They cannot count as current root03 media.
`summarize` consumes exactly17 parent-selected repeated `--inspection` paths,
rejects duplicate case/run selections and never discovers latest results or
silently filters failures. Root03 needs fresh projects/caches and unique
inspection destinations for the changed producer; its two cold pairs validate
actual changed behavior and remain excluded from warm medians. The unchanged
SOURCE fixture contains17 files, including all nine original lossless WAVs.
The three delivered literal selections agree at quality-media03: helper TARGET,
Node metadata's host projectRoot suffix and host FFmpeg's canonical project
suffix. All other schema/case/run/part, canonical cwd/install
path checks, forwarding, streams, environment, exits, signals, lifecycle and
measurement behavior remain unchanged. Existing14 helper specifications patch
TARGET to their fixtures and remain unchanged; helper-tests06 supplies the actual
refresh. Current original CLI, Node rendering and Docker final exercised both
observers at root03. No new root option, retry,
management hash or product CLI is introduced.

### Generic SceneCard lower-resolution layout

The generic SceneCard uses actual selected composition width and height,
independently of profile name, fps and audio. Its shared scale is
`min(1, width / 1280, height / 720)` for resolved positive dimensions.
Body font56px, padding80px, scene-label font24px, bottom48px and right64px
are multiplied together by that scale. At854×480 scale is2/3; at960×540 it
is3/4. At1280×720 and whenever both dimensions meet or exceed that reference,
all five reference values are preserved exactly, without font enlargement.

Preserve every original string, line-height1.25, font family, colors,
alignment and scene ID, scene-local0 Audio, scenes/timing/effects/assets/credits,
encoding and props schema. Clipping must not be concealed by crop, hidden
overflow, ellipsis, truncation or shorter fixtures. Character dialogue,
character web-demo and simple-java2d layout are outside this change.

The delivered executable contract
[CozyVideoSceneLayoutSpec](../../src/test/scala/cozy/video/CozyVideoSceneLayoutSpec.scala)
executes the exact shared private JavaScript layout helper through Node, with
Given/When/Then, should matchers and ScalaCheck properties for proportional
lower-resolution scaling and capped reference values. Its5 specifications and
163-specification refresh of CozyVideoSpec, CozyVideoQualityRouteSpec,
CozyVideoMixedRenderSpec, CozyVideoProfileRenderSpec and CozyVideoMediaEndingSpec
passed, with one explicit opt-in Docker regression cancellation. These receipts
remain at their original scope; three test-only selectors require no Scala
change or unconditional SBT refresh. Root03 selector delivery/helper validation
and all17 technical/visual observations are recorded at checkpoint13; gap14
reassessment and Step/Phase acceptance remain pending. Numeric specifications
do not establish visible usefulness: all17 direct/final pairs require full native technical checks,
all12 PNGs per movie (408 across34 movies) covering all nine original scenes/opening/summary/endcard,
readable unclipped text and distinguishable labels. Full/tail source thresholds
remain0.80/0.60, rational A/V ends within one48k sample, complete endcard holds,
and both-route three-pair warm median speed requirements remain unchanged.
