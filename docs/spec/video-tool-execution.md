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
simple-java2d also retains stillimage tune. Hardware uses bitrate defaults of
2M/lightweight, 6M/standard or 12M/quality, overridden by explicit bitrate,
and emits `-c:v h264_videotoolbox -b:v <bitrate> -allow_sw 0 -require_sw 0`.
Hardware has no CRF, preset or tune and never falls back. Hardware frame props
require a valid encodingPolicy; legacy software props may omit it.
Both policies use yuv420p. The frame route resamples stereo48k, pads/trims to floor(N*48000/fps),
encodes AAC320k with cutoff18000, movie_timescale48000 and video timescale
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
