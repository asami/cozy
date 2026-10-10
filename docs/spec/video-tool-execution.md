# Video tool execution

Status: PHASE-71.6 S01 implementation contract; acceptance pending.

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

Software encoder is `video.encoder.name=libx264`. Unconfigured all-Docker and
whole-operation host rendering retain the normal Remotion renderMedia branch.
Explicit encoder, FFmpeg executable/image, or different Remotion/FFmpeg mode
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

FFmpeg consumes exactly N PNGs and PCM audio, encodes libx264 with profile
CRF/preset and yuv420p, resamples stereo48k, pads/trims to floor(N*48000/fps),
encodes AAC320k with cutoff18000, movie_timescale48000 and video timescale
fps*1000. No shortest truncation. Final card, last-frame duration and all scene
timing remain the complete composition's timing. Outputs use normal overwrite
and process/I/O behavior. Part/build diagnostics record effective selection,
argv, exits, paths and frame count.

Executable contracts: CozyVideoExecutionSpec and CozyVideoMixedRenderSpec.
Focused specifications and actual native software video/audio/EOF evidence
remain parent-owned acceptance gates. Hardware is deferred until that evidence;
replay/transcription model compatibility is S03. Repository-full validation,
remaining native combinations and benchmark remain deferred-not-run to 71.7.
