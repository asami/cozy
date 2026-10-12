# Video tool execution

Status: PHASE-71.6 all Steps accepted and committed: S01 at `eb3664c`,
S02 at `3a4ca3e`, S03 at `fa856382741d9dc0e84c1e4b3c14dc2a480be859`.
Phase closed projection: 2026-10-11 under `P716-RELEASE-20261011@1`, effective
only when the parent verifies the distinct native release commit carrying that
binding. Sole full Phase review `107704e9-a669-40fa-98c2-6f040484b9ee@1`
is accepted PASS at ledger revision 8. Latest focused validation: 233/233
specifications, nine suites, no warnings, SBT/wrapper exits 0/0 and lock release.
Repository-full SBT, remaining native combinations and benchmark remain
deferred-not-run to PHASE-71.7, planned and unstarted. Accepted legacy-spec
workspace, Model-size and macOS say workspace Hygiene remain separate
maintenance in the canonical Hygiene journal; resolved header history is retained.

## Operating settings

`video.tools.<tool>.mode`, `.executable` and `.docker-image` address exactly
remotion (node), ffmpeg, ffprobe, playwright (node), python-pillow (python3),
whisper-cpp (whisper-cli), piper (textus-toolchain) and macos-say (say).
Portable tools default to Docker; macos-say supports only host and Piper only
Docker. VOICEVOX remains an explicitly configured HTTP service; docker is a
host launcher prerequisite. Availability never selects an execution mode.

Each field resolves independently. Applicable explicit legacy CLI mode/image
wins, then the highest per-tool operating field, then descriptor
tools.toolMode/dockerImage, then video.tool-mode/video.docker-image/
cozy.docker-image, finally the tool default. Existing operationDefaultFiles
ordering is authoritative: ~/.cozy, project conf/cozy, project .cozy, with the
loader's file order within each directory. Executable uses the highest
per-tool field or its logical command. Effective fields retain their origins.
Unknown tools, modes or fields, non-object maps, blank authored fields,
executable control characters, and same-layer host plus a per-tool image fail.
A lower-layer or global image may remain unused for a host selection.
Executable is one argv element, without expansion or command splitting.

## Rendering and encoding

PHASE-71.8 P718-01 adds the separate
[joint quality contract](video-quality-profiles.md), with current listening and
independent Step acceptance and exact local commit complete.
Canonical `qualityProfile` supplies hardware defaults 1M/lightweight,
2M/standard, 6M/quality and 12M/high-quality. Original `encodingPolicy` props
retain historical 2M/lightweight, 6M/standard and 12M/quality meanings.
Present canonical props are authoritative and validate even when operating
bitrate is explicit. Video bitrate override/origin stays independent of audio.
Resolved audio defaults have current JA/EN listening acceptance.
P718-02 is CLOSED on2026-10-12 at local Step commit
`3befd92f53c0bc6919e1e5be2cc52b266e3e0279`: native/mixed and generated Docker mux routes consume
resolved audio and observations carry canonical/audio/seven origins under the
[joint wire contract](video-quality-profiles.md#generated-joint-observations-and-legacy-inputs).
Its18 route and228 accumulator specifications and independent focused review
PASS are accepted. P718-03 checkpoint13 records all17 selected root03
technical/visual observations, with lightweight warm whole-pair medians32.5%
shorter on mixed and38.5% shorter on Docker. There are16 complete case-runs
and gap14 requiring fresh independent reassessment; Step/Phase acceptance remains
pending.

Encoder name is exactly `libx264` (default) or `h264_videotoolbox`.
`video.encoder.bitrate` resolves independently of name, with its own origin.
Strings are trimmed and match `[1-9][0-9]*(?:[kM])?`, where k scales by 1000
and M by 1000000. JSON numbers must be positive integers and normalize to
integer argv text. The scaled value must fit positive signed 64-bit. Every
authored layer validates even when shadowed; unknown fields, malformed maps,
types, names, blank/invalid/fractional/overflow bitrates fail. Name and bitrate
may come from different layers. Resolved VideoToolbox requires host FFmpeg;
bitrate with resolved libx264 fails before producers. No CLI or descriptor
encoder setting is introduced.

Unconfigured all-Docker and
whole-operation host rendering retain the normal Remotion renderMedia branch.
Explicit encoder, hardware name, FFmpeg executable/image, or different Remotion/FFmpeg mode
or image selects external encoding. There is no fallback.

The external branch bundles and selects the same full CozyVideo composition
with unchanged props. renderFrames writes all N PNG frames; renderMedia exports
the full composition as WAV with pcm-16, imageFormat none and enforceAudioTrack.
Remotion owns narration/audio mixing; its audio encoder dependency remains
inside its selected runtime. Selected FFmpeg owns external H.264 video encoding.

The Cozy handoff `cozy.video.frame-audio.v1` has width, height, fps, frameCount,
ordered relative PNG paths and relative PCM audioPath. Dimensions/rate/count
are positive finite values; N equals planned totalFrames. Indices are unique,
complete and ordered. Files must exist safely inside the workspace. Remotion
assetsInfo and container absolute paths are never serialized. These are schema
and ordinary I/O checks, with no hashes or directory-set certificates.

FFmpeg consumes exactly N PNGs and PCM audio. Software uses profile CRF/preset;
simple-java2d also retains stillimage tune. Hardware uses canonical defaults
above when present, otherwise legacy 2M/lightweight, 6M/standard or 12M/quality,
overridden by explicit bitrate,
and emits `-c:v h264_videotoolbox -b:v <bitrate> -allow_sw 0 -require_sw 0`.
Hardware has no CRF, preset or tune and never falls back. Hardware frame props
require a valid qualityProfile or legacy encodingPolicy; legacy software props
may omit both.
Both policies use yuv420p. The frame route resamples stereo48k, pads/trims to floor(N*48000/fps),
encodes selected-rate AAC with cutoff18000, movie_timescale48000 and video timescale
fps*1000. No shortest truncation. Final card, last-frame duration and all scene
timing remain the complete composition's timing. Outputs use normal overwrite
and process/I/O behavior. Part/build diagnostics record effective selection,
argv, exits, paths and frame count.

`effectiveEncoder` in external encoding diagnostics and final build manifests
comes from the argv policy. Inspect and dry-run retain authored profile,
CRF/preset and show effective bitrate/origin, active quality controls and
hardwareRequired. That flag expresses selection intent, never proof of actual
hardware execution. Hardware initialization failure retains actual diagnostics
and ordinary partial outputs without retry. Build remains stream copy with
independent probe.

Generated Remotion props always include qualityProfile/audioBitrate/encodingOrigins;
missing-all legacy props retain320k. Present canonical requires valid canonical
and audio, with null/malformed/overflow rejected. Docker pre-stitcher audio stays
PCM and its sole libfdk_aac stitcher uses selected audio. Multi-part narrated
build independently decodes inputs, allocates cumulative rational sample floors,
copies video, concatenates PCM and encodes AAC once at the root-selected rate.
Single-part/video-only assembly keeps copy behavior. Simple-java2d uses selected
audio, cutoff18000, ar48000/ac2 and its existing shortest/stillimage controls.
Part/project/Storyboard/RDF/review intent records report joint selection and
origins; external encoding records also contain actual argv. No native/Docker
execution or measured bitrate is inferred from intent. The mandatory generated
callback fixture in CozyVideoQualityRouteSpec uses local Node modules and proves
callback arguments only; actual media acceptance remains P718-03.

P718-03's selected measurement route preserves ordinary Docker renderMedia
for both canonical tiers: direct Docker FFmpeg keeps mode=docker, its standard
executable and inherited common image, with no per-tool Docker image or
operating encoder override. Mixed cases explicitly select software libx264
and the host FFmpeg observer. Both routes use the selected Remotion Node
observer. Only prepare-final switches Docker FFmpeg executable to
`/workspace/quality-tools/ffmpeg-observed.mjs`, keeping mode=docker and the
common image; the next warm prepare restores the ordinary direct selection.
This basename-selected Node proxy forwards FFmpeg argv in the container and
does not instrument Remotion's internal FFmpeg binary. Mixed host forwarding
uses the existing selected host executable unchanged. Final-only observer
selection is equal for both profiles.

Observer traces retain monotonic durations, actual argv, case/run/part and
invocation metadata. Bundle/selectComposition/renderFrames/audio-mix and host
encode are separate stages; Docker renderMedia has overlapping rendering,
encoding and progress windows plus actual pre-stitcher/stitcher arguments.
Callback arguments are not native subprocess logs. Parent-matched native
commandExecution ID/command/durationMs supplies tool wall time, including
executor/lifecycle bookkeeping but excluding agent creation/queue. File mtimes
are diagnostic only. No substitute stopwatch, missing-duration fallback,
cross-domain clock subtraction or overlap sum establishes speed.
Private inspection follows explicit emitted template scheduling:
generic `rendererTemplate=null` uses openingFrames plus accumulated scene
start; the known character dialogue/web-demo templates add leadInFrames to
openingFrames plus startFrame. It validates authored allocation, source/full
tail preservation and both copies; missing/unknown templates fail without
origin search. Existing generic lead metadata does not prove silence is
scheduled; `DEV-P718-03-GENERIC-AUDIO-TIMING-01` remains separate untouched
behavior/design follow-up, open to independent scrutiny.

`inspect --output-directory` selects a new safe owned inspection
directory without overwriting the retained failed attempt. Repeated
`summarize --inspection` inputs are parent-selected exact paths, with duplicate
case/run rejection and no implicit latest selection or failure filtering.
These are private helper selectors; the exact Cozy command surface and all17
successful inspection/34 command duties stay unchanged. No inspection may run
during timed warm comparisons. Root01 cold technical inspections, wrong-origin05,
snapshot-collision06 and lightweight clipping07 remain historical. The delivered generic SceneCard correction
uses actual composition dimensions and the capped1280×720 scale specified in
[the layout contract](video-quality-profiles.md#generic-scenecard-lower-resolution-layout).
It changes no audio/Sequence/props/observer or native execution route.
CozyVideoSceneLayoutSpec passed5 specifications and the five affected suites
passed163, with one explicit opt-in Docker cancellation at layout04 scope.
Earlier helper-tests05 passed14 scenarios in1.030s. Root02's first mixed-standard CLI failed at observer
tool checking (exit1,8.988s), before a new movie: Node metadata and host FFmpeg
still selected root01 while helper TARGET selected root02
(`P718-03-OBSERVER-ROOT-SELECTION-08`).
The complete17-case/run,34-command matrix was observed in
`target/phase718/quality-media03/`, with explicit selected inspection paths and
all12 PNGs/movie individually viewed (408 across34 movies). Three coherent
literal selections were delivered at root03: helper TARGET, Node metadata host
projectRoot suffix and host FFmpeg canonical project suffix. All other guards/forwarding/argv,
streams/environment, exits/signals/lifecycle and measurement conventions remain
intact. Existing14 helper specifications remain unchanged; helper-tests06
passed in1.034s. Original CLI, Node rendering and Docker final exercised both
observers at root03. There is no new root API,
retry or unconditional SBT refresh. Root01 movies/native records/PNG failures
and root02's prepared project/terminal failure stay retained without credit
as root03 results. Checkpoint13 records17 technical reports/561 real checks,
34 fully decoded H264/AAC stereo48k movies, rational ends within one sample,
nine/eighteen original WAV full≥0.80/tail≥0.60 matches and all two-second end-card
frames. Selected original texts/labels/opening/summary/credits are readable and
unclipped. Mixed lightweight/standard warm whole-pair medians are99.736/147.785s
(32.5% shorter); Docker80.229/130.418s (38.5% shorter), cold excluded. Native
totals include lifecycle/tool/observer overhead, exclude agent queue/idle and
make no process-hot/system-cold or uninterrupted-clock claim. Docker overlapping
render/encode windows are neither summed nor called pure encode.
The mixed cold override observed960×540@15fps/CRF28/veryfast/AAC192k, six explicit
field origins and quality-profile selection,4023/8046 direct/final frames and
one final AAC after independent PCM decodes. Its157.238s total is excluded from
speed. There are16 complete case-runs plus `P718-03-RUNTIME-INSPECTION-REPLAY-14`:
docker-standard/warm02 first inspection exited0 with33 checks/24 viewed PNGs;
the duplicate attempt exited1 at the existing-directory guard before probing.
Original initial-exec/renderer API objects remain missing, runner completion
is INCOMPLETE, and fresh independent Step reassessment is mandatory. Aggregate
observations do not discharge this gap or grant acceptance. Final full test,
runtime classpath export, review/commits and Phase release remain pending.
Protocol, current results, cold facts and retained failure evidence are recorded in the
[P718-03 journal](../journal/2026/10/2026-10-12-phase-71.8-quality-media-acceptance.md).

Executable contracts: CozyVideoExecutionSpec, CozyVideoMixedRenderSpec and
CozyVideoHardwareEncodingSpec. S01 software native evidence is accepted.
Focused specifications and hardware generation/decode/audio/EOF/hearing
have parent-admitted Step acceptance;
S02 hardware native generation, decode, EOF and current user hearing are accepted.
Repository-full validation,
remaining native combinations and benchmark remain deferred-not-run to 71.7.

## Transcription and replay consumers

Transcription resolves the ordinary operating layers and retains only FFmpeg
and whisper-cpp selections in its source-compatible adapter. Existing four-field
construction retains global dispatch. The global mode/image and result fields
remain compatibility summaries; there is no binary-compatibility claim.
Selected Host whisper requires the existing CLI/YAML host model precedence,
even with global Docker. Selected Docker whisper uses
`/opt/textus/models/ggml-base.bin`, even with global Host. All authored operating
layers validate through the common resolver; no new CLI options are added.

Before work creation, project/input/save paths normalize to absolute paths.
Selected Docker FFmpeg requires input within the project. Either selected
Docker consumer requires save/audio/output within the project. Both Host permit
external input and save; Host FFmpeg with Docker whisper permits external input.
Command and path dispatch identify the consuming tool explicitly. No mount or
container model expansion is introduced.

Preflight requires selected FFmpeg, selected Host whisper, and daemon/image
checks only when either consuming tool is Docker. Unrelated checks and images
do not gate transcription. Required Missing, Unchecked and Unsupported fail with
the existing contextual hint; absent checks in partial injected registries remain
compatible. Docker whisper stays image-managed Unchecked: only its actual
process proves execution. Extraction failure stops before whisper; whisper
failure has no retry, and an optional version failure is nonfatal.

Transcription and replay manifests append `effectiveTools` from their actual
execution settings without changing schemas, old summaries or output shapes.
Replay retains selected Playwright paths, argv, recording and dry-run behavior;
required Unsupported also fails. Public inspection/dry-run preserves authored
profiles, effective encoders, normal renderMedia and independent ffprobe.
CozyVideoCompatibilitySpec supplies consumer contracts with injected producers;
these fixtures are never native evidence. Existing mixed-render and hardware
specifications retain rendering, narration, encoding and ending coverage.
