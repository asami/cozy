# Phase 71.6: Per-Tool Execution and Video Encoder Selection

status=in-progress
depends_on=phase-71.5.md
execution_priority=after_phase_71.5_closure
split_full_test_policy=final-only
split_full_validation_method=sbt-full-suite
split_validation_bootstrap=none
validation_ownership=aggregate-deferred
aggregate_validation_owner=PHASE-71.7
aggregate_validation_sequence=["PHASE-71","PHASE-71.1","PHASE-71.2","PHASE-71.3","PHASE-71.4","PHASE-71.5","PHASE-71.6","PHASE-71.7"]
primary_owner=Cozy

Status: in-progress; S01 accepted and committed at `eb3664c`; S02 accepted and committed at `3a4ca3e`; S03 consumer implementation pending validation/review
Planned at: 2026-10-05 (Asia/Tokyo)
Development item: DEV-036
Predecessor: [Phase 71.5](phase-71.5.md).
Completion ledger: [Phase 71.6 Checklist](phase-71.6-checklist.md).
Source: [encoder selection and benchmark request](../journal/2026/10/2026-10-05-video-encoder-selection-and-benchmark-request.md).

## Goal

Let users explicitly select host or Docker execution for each participating
external tool and select software or hardware video encoding through local
operating configuration. Connect those choices to the actual video producer,
including Docker frame rendering with host encoding. Complete implementation
and corresponding Executable Specifications while preserving video/narration
behavior. Demonstrate the real software mixed route first, then selected
hardware execution here. Remaining combinations and measured comparison
belong to Phase 71.7.

## Placement and ownership

Execute after Phase 71.5 closes. Phase 71.4 owns video-ending implementation;
Phase 71.5 owns removal. Phase 71.6 closes on feature implementation and passing
Executable Specifications plus real representative mixed/software and selected
hardware execution, with normal review/commits. Remaining native combinations,
benchmark and repository-full validation move to Phase 71.7.
Phase 72 and Phase 75 are not technical prerequisites, and their scopes and
identities remain unchanged. This planning decision does not start execution.

The owner is Cozy. Use the existing tool/runtime integrations and supported
renderer APIs; no external repository implementation is adopted by this plan.
Freeze the exact implementation files against the post-71.5 tree at entry.

## Execution plan — 2026-10-08

Implement on the accepted 71.5 contract, preserving the 71.4 ending semantics.
P716-01..03 are one connected feature, not independent configuration, helper
and producer review projects. P716-04 remains transferred to P717-03.

1. **Working baseline and settings through producer invocation.** Reuse the
   accepted 71.5 route, inputs and ordinary outputs. Fix the finite per-tool key/
   precedence/legacy `--tool-mode` behavior, then connect it to the actual video
   producer and its effective-command/result output in the same batch. Include
   Remotion, FFmpeg, ffprobe and the known participating calls. Test Docker
   defaults and explicit host selection through the ordinary entry; do not
   leave settings consumed only by a test helper until final integration.
2. **One real mixed route before encoder breadth.** Implement Docker frame
   rendering -> host software encoding as the common handoff and run the normal
   Cozy entry to a final representative video with audible narration and correct
   ending. Do this before implementing all selections or parameter combinations.
   Resolve intermediate frame/audio paths, counts, quality controls and final
   assembly together, including renderer-owned encoding. Write connected Specs
   alongside implementation for selection, handoff, narration/end preservation
   and ordinary failures. Keep the accepted Docker/software baseline usable.
3. **Add selected hardware on the working route.** Apply VideoToolbox options
   to that same route and actually run it on the supported machine; then finish
   the remaining finite configuration/compatibility branches with affected Specs.
   Confirm the selected encoder was used and inspect/listen to its real output.
   Listing an encoder or generating its command is not execution. Missing
   selected prerequisites leave this outcome open, with no silent fallback.
   No timed matrix or speedup target is required here.
4. **Focused acceptance and integration handoff.** Run corresponding Specs and
   required consumer checks, resolve concrete defects, and complete normal
   independent review and release. Hand 71.7 the callable commands, local
   settings, actual software/hardware media results and known environment
   prerequisites. Fix broken main-route connections here. Remaining combinations
   and performance results stay in 71.7, not first-ever native execution.

Check supported renderer interfaces and selected tool/encoder prerequisites
while implementing so missing integration contracts surface early. This does
not start external commands through this planning edit or make uninstalled
hardware a passing result. Reuse assets and diagnosis; do not introduce another
renderer/executor framework or every tool-option cross product.
Use ordinary outputs and failures; do not reintroduce 71.5's removed protection.

## Scope and planned Steps

### P716-01: Per-tool operating configuration

- Define design, specification and Executable Specifications for independent
  host/Docker selection of the video pipeline's external tools, including
  Remotion, FFmpeg and ffprobe, and inventory other invoked tools.
- Use `~/.cozy/config.*` for machine-specific operating settings; no
  `~/.textus/*` setup is required. Unselected execution defaults to Docker.
  Installed-tool discovery reports availability and never silently selects a
  different execution environment.
- Define tool identity, executable/image selection, configuration keys and
  precedence across local settings and existing CLI/project options. Specify
  compatibility for the existing global `--tool-mode` before changing it;
  reject contradictory input rather than silently ignoring a selected value.
- Keep machine-specific paths and hardware settings separate from portable
  storyboard/content and media-quality intent. Expose effective per-tool
  settings through the existing inspection and execution output.

### P716-02: Software and hardware encoder invocation

S02 implements the frozen host VideoToolbox contract after accepted S01 commit
`eb3664c0e95484948c043a761674f4229ab9118e`. Encoder name and bitrate resolve
independently from operating layers. Bitrate defaults are 2M/lightweight,
6M/standard and 12M/quality; explicit positive signed-64-bit bitrate overrides
the profile default. Hardware requires host FFmpeg, forbids software fallback
and omits x264 CRF/preset/tune. Both frame and still producers use one shared
policy, also projected beside authored settings in inspection and manifests.
Focused validation, actual hardware initialization/render/build/decode,
unmuted natural EOF and current hardware hearing are accepted. Independent
protected-focused Step review is accepted after its sole blocker was repaired
with the parent-verified M1 waiver. The exact local S02 Step commit is verified at
`3a4ca3ec623d0b836bea5aa21d8c60cc7162322a`.
Configured hardware intent alone is not proof of hardware execution. See the
[S02 journal](../journal/2026/10/2026-10-11-phase-71.6-hardware-route.md).

- Keep execution environment, frame renderer and video encoder separate.
  Host execution does not imply hardware encoding.
- Bind the configured host executable and encoder-specific options to actual
  commands. Retain software H.264 as an explicit comparison baseline and
  support the requested macOS VideoToolbox H.264 path.
- Define how existing quality profiles map to encoder-specific controls;
  do not blindly pass x264/CRF options to hardware encoders.
- Apply selection to the renderer-owned encoding path as well as direct Cozy
  FFmpeg calls. An explicitly selected hardware path must report unsupported
  selection or initialization failure instead of silently using software.
- Use actual invocation/result and available encoder diagnostics to report
  hardware use. Listing an encoder alone is not successful hardware execution;
  unavailable evidence is reported honestly, without inventing a telemetry or
  unchanged-artifact proof system.

### P716-03: Mixed rendering and encoding

- Implement Docker Remotion frame rendering followed by host FFmpeg encoding,
  for both software and the supported hardware encoder.
- Define the intermediate frame/audio handoff, host/container path mapping,
  frame rate/count and audio-mix responsibilities using supported APIs.
  Preserve narration coverage, assets, timing and the accepted final-card and
  audio/video ending behavior. Merely changing a later FFmpeg invocation does
  not satisfy this mixed-execution requirement.
- Preserve Docker/software and complete host rendering paths. Diagnose
  unsupported combinations and missing selected tools explicitly.
- Use ordinary temporary outputs and normal process/I/O error handling.
  Do not restore the protection mechanisms removed by Phase 71.5, introduce
  hashes or comparison copies, or prove intermediates have not changed.

### P716-04: Measured comparison and acceptance — transferred

The existing five-case procedure is consolidated into
[P717-03](phase-71.7.md#p717-03-native-execution-and-benchmark). It remains
unexecuted and is not a Phase 71.6 exit condition. This Phase supplies commands,
settings and actual representative software/hardware results. Phase 71.7
extends coverage and measures performance; it is not the first real connection.

## Validation and closure

Executable Specifications cover Docker defaults, independent selections,
configuration precedence/compatibility, actual command options, invalid or
unsupported selections, and preserved media semantics. Native execution proves
the supported mixed and hardware paths separately from command-construction
tests; representative mixed/software and selected hardware acceptance belong
here, with remaining combinations in Phase 71.7. Record ordinary commands,
exit results and test logs without unchanged-input proofs.

After implementation, focused Specs and real representative routes pass, complete
independent Phase review and the normal distinct Phase release commit. Remaining
native combinations, benchmark and aggregate full suite are deferred to Phase 71.7.
This is a planned obligation, not validation performed by this document.
No publication, deployment or modification of external product drivers is
included. Benchmark outputs stay in task-owned locations.

## Non-goals

- Automatic host/Docker or hardware selection based on installed tools.
- A general remote executor, scheduler or automatic benchmark-based tuning.
- A new rendering engine, altered narration/content, or reopening Phase 71.4.
- Hardware support for every platform or encoder in this first delivery.
- Changes to Phase 72 Failure Model or Phase 75 site-production repairs.
- Artifact protection, unchanged-input proofs, or duplicate comparison ledgers.

## References

- [Request and benchmark procedure](../journal/2026/10/2026-10-05-video-encoder-selection-and-benchmark-request.md)
- [Phase 71.4](phase-71.4.md) — accepted media-ending behavior to preserve
- [Phase 71.5](phase-71.5.md) — predecessor and protection-removal policy
- [Phase 71.7](phase-71.7.md) — native integration, benchmark and aggregate suite
- [Phase 31](phase-31.md) — existing encoding policy profiles

## S02 current parent-admitted evidence — 2026-10-11

The earlier S02 pending observations above describe the implementation handoff.
Current focused specifications and native automated hardware validation passed;
hardware human hearing and independent S02 acceptance remain pending. The
selected eight suites passed 214/214 in one registered serial SBT invocation,
with SBT/wrapper exits 0/0 and terminal lock release. No compile warning lines
were observed. The same code remains current; no full suite or repeated compile
was run.

Normal representative JA34 Docker Remotion frame/PCM production -> host FFmpeg
9.0.1 `h264_videotoolbox` with `2M -allow_sw 0 -require_sw 0` succeeded, followed
by ordinary Cozy build with `-c copy` and probe. Actual output: 104120282 bytes,
8091 frames at 18 fps, 1280x720, 449.5 seconds, 48 kHz stereo AAC. Independent
direct/final full decode exited zero; all 34 ordered WAV windows were covered,
36 final card frames were legible, and presented and packet A/V ending
differences were zero.

Fresh hardware-player02 reached start 0 -> natural 449.5-second EOF with volume
1, unmuted, rate 1, no seek and media error null. Its entire machine-rendered
`execution_request`, including the full 14-element prefix and justification,
was retained and submitted unchanged through separate store/load tool calls.
The same native handle 18725 reached terminal exit 0; ownership, readiness,
clients and cleanup were true, and the process was terminal. Player02 native
stderr was empty ("Empty log"). This is automated playback evidence only.

Retained execution gaps remain factual history: render transport loss left the
original producer running, with terminal receipt and owned group absence
independently observed and no duplicate render; decode01's manually copied
prefix mismatch retains its observations, with clean decode02 used; player01's
manually copied prefix/shortened justification mismatch retains natural EOF and
cleanup observations, with clean player02 used. Player01's two browser-request
`BrokenPipeError` stderr traces remain recorded alongside media error null,
natural EOF and passed owned listener cleanup. The parent independently read
the actual helper and rendered 14-element prefix; no shared-skill correction
was made. See the [S02 journal](../journal/2026/10/2026-10-11-phase-71.6-hardware-route.md)
for the retained checkpoint and receipt locations.

The earlier human text `自然に聞こえた。71.6を再開して` applies to S01 software
output. Current hardware hearing, fresh independent protected-focused S02
review and exact Step commit remain pending. S03 compatibility, one full Phase
review and the distinct release remain open. Aggregate full suite, remaining
native matrix and timed benchmark remain deferred-not-run to Phase 71.7.
This documentary update makes no acceptance or Phase closure decision.

## S02 hearing and Step review acceptance — 2026-10-11

This current annotation supersedes the earlier pending observations without
changing their history. The user confirmed the current hardware output with
`自然に聞こえた`; it is distinct from S01 software hearing. The native output,
natural EOF, owned cleanup and media observations above remain applicable.

The independent protected-focused S02 review found one blocker, CPB-S02-R01:
the new hardware specification allocated its workspace outside `target/`.
The correction changes only that private helper's allocation parent to
`target/cozy-video/work/hardware-spec`. All 12 existing hardware specifications
passed after the correction, with no warnings, SBT/wrapper exits 0/0 and shared
lock release. The other 202 cases from the earlier eight-suite run remain
applicable; the eight suites were not rerun as a whole.

The parent verified the exact repair and admitted its M1 waiver. The original
independent FINDINGS review and the repair history remain recorded; the Step
review closure has zero current blockers at ledger revision 6. Existing fixture
placement maintenance, HYG-P716-S02-LEGACY-SPEC-WORKDIR, is persisted separately
in the [Hygiene journal](../journal/2026/10/2026-10-10-phase-71.6-hygiene-follow-up.md).
This is Step review acceptance, not the still-required full Phase review.

S02's exact local Step commit is verified at
`3a4ca3ec623d0b836bea5aa21d8c60cc7162322a`, with parent
`eb3664c0e95484948c043a761674f4229ab9118e`, using the retained
`s02-step-commit-checkpoint.json` result `STEP_COMMITTED_VERIFIED`.
S03 remaining consumer
compatibility, the sole full Phase review and the distinct final release remain
open. Aggregate full validation, remaining native combinations and timed
benchmark remain deferred-not-run to Phase 71.7.

## S03 consumer implementation handoff — 2026-10-11

The frozen consumer contract extends the source-compatible transcription adapter
with operation-scoped FFmpeg/whisper selections, selected model/path/preflight
semantics and effective manifest projection. Replay retains selected Playwright
dispatch and adds effective projection and required Unsupported handling.
CozyVideoCompatibilitySpec documents public mixed/default/Host behavior with
injected contract fixtures. These outputs supply no native acceptance evidence.
Existing S01 software and S02 hardware native/current hearing remain accepted.

S03 focused validation, independent review and exact Step commit are pending.
The sole full Phase review and distinct verified release remain pending. The
[S03 journal](../journal/2026/10/2026-10-11-phase-71.6-consumer-compatibility.md)
records the frozen scope and handoff. Model-size follow-up is a parent PLAN
candidate pending independent classification, not an accepted finding or closure.
