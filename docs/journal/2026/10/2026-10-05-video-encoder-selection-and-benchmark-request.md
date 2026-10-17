# Video encoder selection and benchmark request

Date: 2026-10-05 (Asia/Tokyo)
Owner: Cozy
Status: adopted into planned Phase 71.6; implementation and benchmark pending

## User request

In a side conversation, the user requested that Cozy retain both execution
choices and allow measured comparison, after discussing native FFmpeg hardware
encoding on macOS. Preserve host/Docker selection and make software/hardware
encoding an explicit choice rather than assuming that host execution enables
hardware acceleration automatically.

The user subsequently clarified the operational requirements:

- Use `~/.cozy/config.*` for this machine's operating configuration;
  `~/.textus/*` is not required.
- Default to Docker. Select host execution explicitly in configuration;
  do not choose an execution environment automatically from installed tools.
- Resolve execution settings separately for each external tool, including
  FFmpeg and other render, probe or media tools. One global host/Docker switch
  is insufficient for mixed execution.
- For a locally executed encoder, configure the hardware-encoding information
  needed to select its actual invocation. Host execution alone must not imply
  hardware encoding.

This record captures that request for subsequent implementation. It does not
change product behavior, the active Phase scope, or its acceptance checklist.
The initial side conversation added only this journal. The subsequent planning
decision below adds Phase 71.6 and its checklist and updates the Phase index
and strategy; the ongoing video-ending repair and its source files are unchanged.

## Requested placement

On 2026-10-05 the user corrected the earlier Phase 2 / 5 / 6 / 7 references:
the active work is the Phase 71 sequence, and placement should be considered
among its later planned work. The earlier numbers are superseded, not a
direction to reopen the historical completed Phases 5, 6 or 7.

After comparing Phase 71.5 (protection removal), Phase 72 (CML Failure Model)
and Phase 75 (site-production repairs), the user selected a new
[Phase 71.6](../../../phase/phase-71.6.md), with its own
[checklist](../../../phase/phase-71.6-checklist.md). It owns per-tool host/Docker
selection, local hardware encoder configuration, the actual mixed production
path and the comparison procedure below as one feature delivery.

Execution order: Phase 71.4 closure -> Phase 71.5 -> Phase 71.6. Phase 71.6
has its own validation/release and does not extend the Phase 71–71.4 aggregate
full-test chain or become a predecessor closure condition. Phases 72 and 75
are not technical prerequisites and retain their existing scopes.

This is an adopted planning decision, not the start or completion of a Phase.
No implementation, benchmark or active Phase acceptance criteria changed here.

## Current observations

- Cozy already supports `--tool-mode=host` and `--tool-mode=docker`.
- The inspected `VideoExecutionConfig.create` defaults to Docker. Its current
  tool-mode selection applies to the operation's tools rather than supplying
  independent execution settings for each tool. Operation defaults already
  load user configuration from `~/.cozy/config.*`.
- The inspected simple-java2d FFmpeg path explicitly selects `libx264`.
- The inspected Remotion command selects H.264 and CRF/x264 preset settings,
  but does not expose an explicit hardware-encoder selection.
- Local `/opt/homebrew/bin/ffmpeg -hide_banner -h encoder=h264_videotoolbox`
  exited successfully and listed the VideoToolbox H.264 encoder. This proves
  that the installed binary exposes the encoder, not that an actual hardware
  encode has succeeded or that it is faster on this machine.
- The retained synthesis tool-check log reported host FFmpeg/ffprobe available
  and the host Remotion environment unavailable. Native Remotion readiness
  remains a prerequisite for comparing complete native rendering.

On Apple Silicon, VideoToolbox can use dedicated media hardware; GPU activity
alone is not evidence that hardware video encoding is active. References:
[Apple VideoToolbox](https://developer.apple.com/documentation/videotoolbox),
[FFmpeg VideoToolbox encoder](https://www.ffmpeg.org/doxygen/8.0/videotoolboxenc_8c.html).

## Implementation handoff

Promote the chosen API and behavior into design, spec and executable
specifications before changing the producer. Keep execution environment
(host/Docker), frame renderer (for example Remotion), and video encoder
(software/hardware) distinct. Resolve the execution environment per external
tool from explicit operational configuration, with Docker as the default.
Retain the current software path as an explicit comparison baseline.

For host FFmpeg, the effective configuration needs to identify the selected
executable and encoder, plus encoder-specific settings needed to construct
the invocation. For example, selecting `libx264` versus `h264_videotoolbox`
changes the encoder arguments, not necessarily the FFmpeg executable.
Hardware selection must reach the actual command, rather than remain a
descriptive configuration field. Exact configuration keys, precedence against
existing CLI/project settings, and CLI option names need design/spec definition;
they are not implemented or finalized by this journal.

Include the mixed case where Remotion runs in Docker and FFmpeg runs on the
host. Inspect the renderer-owned FFmpeg invocation as well as Cozy's direct
FFmpeg invocation: changing only the latter does not establish per-tool
selection throughout production. Define the required producer boundary before
implementing that combination; no cross-process redesign is performed here.

An explicitly selected hardware run must report the effective encoder and
whether hardware encoding was actually used. Do not count an unnoticed
software fallback as a hardware benchmark. Report an unsupported selection or
failed hardware initialization as such. Preserve narration, assets, frame
counts and the established audio/video ending contract in either path.

Executable specifications should document Docker defaults, explicit independent
tool selections, host encoder configuration reaching the effective command,
unsupported combinations and preservation of existing media semantics.
Actual hardware execution is separate native evidence.

## Comparison to perform

| Case | Frame renderer | FFmpeg execution / video encoder |
| --- | --- | --- |
| Baseline | Docker | Docker / existing software H.264 path |
| Mixed software | Docker | Host / software H.264 |
| Mixed hardware | Docker | Host / VideoToolbox H.264, actual hardware use confirmed |
| Native software | Host | Host / software H.264 |
| Native hardware | Host | Host / VideoToolbox H.264, actual hardware use confirmed |

Run only combinations supported by the implemented producer. Keep the frame
renderer fixed when isolating the effect of FFmpeg execution and encoding.

Use the same representative storyboard, pre-generated narration, assets,
resolution, fps and effects. Give each run a separate task-owned output tree.
Measure frame rendering, encoding, final assembly and total elapsed time
separately so that an encoding improvement is not mistaken for a rendering
improvement. Record versions, machine, effective settings and output size.

Record cold startup separately from warmed runs; report the median of three
warmed runs per supported case. Avoid concurrent benchmark workloads. Compare
visual quality as well as time and size: equal CRF values are not an equivalent
quality setting across different encoders, so document the chosen comparison
settings explicitly.

Check decoded frames, narration coverage, presented audio/video ends and final
card/player behavior for each result. Include a representative complete Cozy
generation comparison after any isolated encoding measurement. No benchmark
was run while writing this record, and no speedup or quality result is claimed.

## Subsequent completion-scope revision (2026-10-05)

The user subsequently separated feature development from integration acceptance.
Phase 71.6 now owns implementation and corresponding Executable Specifications;
the comparison procedure above, real hardware/mixed acceptance and full validation
move to [Phase 71.7](../../../phase/phase-71.7.md). The earlier placement text is
historical. See the [completion decision](2026-10-05-phase-71-completion-boundaries.md).
No benchmark is claimed and no accepted product result is reset.
