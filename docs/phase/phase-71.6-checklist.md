# Phase 71.6 Checklist: Per-Tool Execution and Video Encoder Selection

Phase status: closed on 2026-10-11 under distinct Phase release binding `P716-RELEASE-20261011@1`; all three Steps accepted and committed; sole full Phase review PASS. This final closed projection takes effect only on parent verification of the distinct native release commit carrying that binding.
Ledger for: [Phase 71.6](phase-71.6.md)
Repository-full validation: deferred-not-run to PHASE-71.7

Completion is feature implementation, passing Executable Specifications and
actual representative mixed/software then selected hardware execution, review
and commits. `[>]` marks remaining integration/benchmark work transferred to
71.7, not a pass or a Phase 71.6 exit condition.

S01 software validation, independent focused review and exact Step commit are
accepted. S02 hardware implementation, native hearing and Step review are
accepted; its exact Step commit is verified at `3a4ca3e`. S03 consumer
compatibility, focused validation and independent Step review are accepted;
its exact Step commit is verified at `fa856382741d9dc0e84c1e4b3c14dc2a480be859`.
The sole full Phase review is accepted PASS at ledger revision 8. The distinct
release is projected under `P716-RELEASE-20261011@1`, subject to the native
commit-verification closure basis below.

Parent observation (2026-10-10): the two new and five planned compatibility
suites passed 196/196 tests in one serialized invocation, with SBT/wrapper
exits 0/0 and lock release. Installed toolchain/host prerequisites are observed.
The first render completed on the baseline route because the parent misplaced
the native fixture settings. Corrected-settings inspect, actual mixed render
and ordinary build passed; direct/final full decoding, 34 scene audio windows,
zero ending difference and the 36-frame final card also passed. Current unmuted
normal-speed browser playback reached EOF at 449.5 seconds without seeking or
media errors, and the owned server exited zero with cleanup confirmed. The user
confirmed natural audible narration on 2026-10-11. Hardware execution and
independent acceptance remain open. The same app Goal is active again and
S01 has advanced to REVIEW; no additional resume operation is required. See the
[S01 journal](../journal/2026/10/2026-10-10-phase-71.6-software-mixed-route.md).

## Entry

Execution mapping (2026-10-08): the [plan](phase-71.6.md#execution-plan--2026-10-08)
connects P716-01 settings to actual producer commands, then runs Docker frames
through host software encoding and final audible playback before adding actual
VideoToolbox execution and remaining configuration branches. Write matching
Specs with the connected implementation and retain normal review/release.
P716-04 performance comparison remains transferred to P717-03.

- [x] Consume Phase 71.5 closure and the accepted video-ending behavior.
- [x] Select exact implementation files from the current tree; preserve unrelated
      work. Reuse the accepted 71.4/71.5 representative input and baseline.
- [x] Identify installed tools and host/Docker/hardware prerequisites for the
      selected main routes here; remaining benchmark cases belong to P717-03.
      Do not select a different environment automatically.

## P716-01: Per-tool operating configuration

- [x] Define design/specification and Executable Specifications for independent
      tool selections with Docker defaults and `~/.cozy/config.*` settings.
- [x] Inventory Remotion, FFmpeg, ffprobe and other participating tool calls.
- [x] Define keys, executable/image settings and precedence, including existing
      CLI/project options and global `--tool-mode` compatibility.
- [x] Keep local hardware/executable settings separate from portable content.
- [x] Expose effective per-tool settings and diagnose contradictory inputs.

## P716-02: Encoder invocation

S02 selected hardware generation, decode, audible natural EOF/current hearing
and protected-focused Step review are accepted under the frozen manifest.
The exact local Step commit is verified at `3a4ca3e`. The checked hardware items below
are supported by actual execution and review, not configuration alone.

- [x] Retain explicit software H.264 and implement host VideoToolbox H.264.
- [x] Pass selected executable, encoder and supported quality controls to the
      actual encoder, including renderer-owned invocation paths.
- [x] Reject unsupported selection or hardware initialization failure without
      silent software fallback.
- [x] Verify actual selected hardware execution using invocation/results and
      available diagnostics here; do not equate encoder availability with
      successful use. Missing prerequisites leave this outcome open.

## P716-03: Mixed pipeline

- [x] Implement Docker Remotion rendering with host software encoding and run
      the normal Cozy entry through final video and audible playback before
      expanding encoder/configuration branches.
- [x] Implement Docker Remotion rendering with host hardware encoding.
- [x] Specify and exercise frame/audio handoff and host/container path mapping.
- [x] Preserve existing Docker/software and complete host paths.
- [x] Pass corresponding Executable Specifications for frame counts, narration
      coverage and timing; actual representative software/hardware final-card and
      audible player EOF acceptance also passes here. Remaining coverage is P717-01/03.
- [x] Use ordinary temporary files and error reporting without new protection
      checks, hashes, comparison copies or unchanged-intermediate proofs.

## P716-04: Benchmark and remaining native combinations — transferred to P717-03

- [>] Run the five-case matrix from the Phase plan on supported environments;
      record missing prerequisites and unsupported cases explicitly.
- [>] Hold storyboard, generated narration, assets, resolution, fps and effects
      fixed and document encoder-specific quality settings.
- [>] Separate cold startup from three warmed runs and report warmed medians.
- [>] Measure rendering, encoding, assembly and total time, including material
      intermediate I/O; record versions, machine, settings and output size.
- [>] Compare visual quality and preserve narration and media-ending behavior.
- [>] Include representative end-to-end Cozy generation after isolated tests.
- [>] Report actual performance without requiring an assumed speedup or
      labeling an unexecuted hardware path as accepted.

## Closure

- [x] Complete exact S01 local acceptance Step commit
      `eb3664c0e95484948c043a761674f4229ab9118e` after software native evidence,
      current hearing, 202 passing focused tests and independent focused PASS.
- [x] Complete S02 selected hardware acceptance and exact local Step commit
      `3a4ca3ec623d0b836bea5aa21d8c60cc7162322a`, verified by the retained
      `s02-step-commit-checkpoint.json` result `STEP_COMMITTED_VERIFIED`.
- [x] Complete S03 remaining consumer compatibility and exact local Step commit
      `fa856382741d9dc0e84c1e4b3c14dc2a480be859`, verified by the retained
      `s03-step-commit-checkpoint.json` result `STEP_COMMITTED_VERIFIED`.

- [x] Focused Executable Specifications pass for selection, defaults,
      precedence, command construction, failures and media behavior.
- [x] Preserve the accepted baseline and record actual representative mixed/
      software and selected hardware results, commands and playback outcomes.
      Only remaining combinations and performance comparison move to P717-03.
- [x] Independent Phase review is complete and actionable findings are resolved.
- [>] Repository-full Cozy SBT validation is consolidated into P717-04.
- [x] Complete the normal distinct Phase release commit and update the ledger
      under `P716-RELEASE-20261011@1`; effective only with parent-verified
      native release commit evidence as specified in the closure basis below.
- [x] Preserve earlier Phase history and record the current aggregate owner as
      Phase 71.7 without rewriting past results.

## S02 current evidence annotation — 2026-10-11

The earlier S02 pending observations retain the implementation-handoff history.
Current focused/native automated validation passed: eight selected suites,
214/214 specifications, one registered serial SBT invocation, SBT/wrapper exits
0/0 and terminal lock release. No compile warning lines were observed. The same
code remains current; no full suite or repeated compile was run.

Normal JA34 Docker Remotion frame/PCM production -> host FFmpeg 9.0.1
`h264_videotoolbox` with `2M -allow_sw 0 -require_sw 0` and ordinary Cozy build
with `-c copy`/probe succeeded. Actual output was 104120282 bytes, 8091 frames at
18 fps, 1280x720, 449.5 seconds, 48 kHz stereo AAC. Independent direct/final
full decode exited zero, all 34 ordered WAV windows were covered, 36 final card
frames were legible, and presented/packet A/V ending differences were zero.

Fresh hardware-player02 reached start 0 -> natural 449.5-second EOF at volume 1,
unmuted, rate 1, no seek and media error null. The entire retained machine-rendered
`execution_request` with its full 14-element prefix and full justification was
submitted unchanged via separate store/load tool calls. The same native handle
18725 reached terminal exit 0; ownership/readiness/clients/cleanup were true and
the process was terminal. Player02 native stderr was empty ("Empty log").
Automated EOF/cleanup does not supply current human hearing.

Retained gaps: render transport loss allowed the original producer to continue;
terminal receipt and owned group absence were independently observed without
duplicate rendering. Decode01's manually copied prefix mismatch retains its
observations; clean decode02 was used. Player01's manually copied prefix and
shortened justification mismatch retains natural EOF/cleanup observations;
clean player02 was used. Its two browser-request `BrokenPipeError` stderr traces
remain recorded, with media error null, natural EOF and owned listener cleanup
passed. The parent independently read the actual helper and rendered 14-element
prefix; no shared-skill correction was made. The
[S02 journal](../journal/2026/10/2026-10-11-phase-71.6-hardware-route.md) records
checkpoint and receipt locations.

The earlier human text `自然に聞こえた。71.6を再開して` is S01 software evidence.
Current hardware hearing, fresh independent protected-focused S02 review and
exact Step commit remain pending; hardware acceptance and Phase closure boxes
remain open. S03 compatibility, one full Phase review and distinct release remain
open. Aggregate full suite, remaining native matrix and timed benchmark remain
deferred-not-run to Phase 71.7. This annotation accepts no review, Step commit or
closure and changes no checkbox.

## S02 current acceptance annotation — 2026-10-11

The user's current hardware observation `自然に聞こえた` resolves hardware hearing;
the earlier software hearing remains separate. The current native render/build,
decode, narration windows, final card, natural EOF and cleanup evidence above
is accepted. The earlier pending annotations remain historical observations.

Independent protected-focused S02 review identified CPB-S02-R01, the new
hardware specification's workspace allocation outside `target/`. The private
allocation parent is now `target/cozy-video/work/hardware-spec`. Its directly
covering 12 specifications passed after the correction, with no warnings,
SBT/wrapper exits 0/0 and shared lock release. The remaining 202 cases from the
earlier eight-suite run are retained as relevant evidence; this was a one-suite
rerun. Parent M1 repair admission resolves the sole current blocker at ledger
revision 6. HYG-P716-S02-LEGACY-SPEC-WORKDIR remains separate persisted
maintenance in the canonical Hygiene journal.

Only the verified encoder and representative mixed-route implementation items
and exact S02 Step commit above are checked. S03 remaining compatibility, the
sole full Phase review and distinct Phase release remain open. The full suite,
remaining native combinations and benchmark remain deferred-not-run to 71.7.

## S03 consumer implementation annotation — 2026-10-11

The frozen S03 changes and CozyVideoCompatibilitySpec are implemented for
parent-owned focused validation and independent review. Public transcription
uses operation-scoped selections; replay records effective settings and rejects
required Unsupported dependencies. Injected fixture output is executable
contract coverage only. S01/S02 accepted native and hearing evidence is retained.
No S03 acceptance, Step commit, full Phase review or release is claimed here;
their boxes remain open. Model-size maintenance remains a parent PLAN candidate
pending independent classification in the canonical Hygiene journal.

## S03 current acceptance annotation — 2026-10-11

This annotation supersedes the earlier pending S03 and model-size classification
text while retaining every historical observation above. S01, S02 and S03 are
accepted and exactly committed at `eb3664c`, `3a4ca3e` and
`fa856382741d9dc0e84c1e4b3c14dc2a480be859`, respectively. The retained Step
checkpoints under `.codex-workflow/phases/PHASE-71.6/` support the checked
implementation and Step acceptance items.

S03 passed 233/233 focused specifications in nine suites, with no warnings,
SBT/wrapper exits 0/0 and terminal serial lock release, as admitted in
`s03-validation02-admission.json`. Independent Step review
`2a96c369-1931-4892-ad5a-1bbd664aee52@1` is PASS with zero Current Boundary
Blockers at ledger revision 7 in `s03-step-review-admission.json`.
HYG-P716-S03-MODEL-SIZE is accepted nonblocking existing Hygiene in the
[canonical Hygiene journal](../journal/2026/10/2026-10-10-phase-71.6-hygiene-follow-up.md).

Accepted S01/S02 actual representative media and current hearing remain
applicable without repeated native execution. Full Phase review and distinct
release remain unchecked and pending. Every `[>]` transfer remains
deferred-not-run to PHASE-71.7; no repository-full validation or benchmark pass
is claimed.

## Phase release closure basis — 2026-10-11

The parent admitted the sole full Phase review
`107704e9-a669-40fa-98c2-6f040484b9ee@1` as PASS with zero Current Boundary
Blockers at ledger revision 8 in
`.codex-workflow/phases/PHASE-71.6/full-phase-review-admission.json`, retaining
the sealed result. All three exact Step commit checkpoints remain verified.
The latest selected validation is 233/233 specifications in nine suites with
no warnings, SBT/wrapper exits 0/0 and terminal serial lock release.

The final closed projection and checked release item are acceptance only when
the parent actually verifies the distinct native release commit carrying
`P716-RELEASE-20261011@1`. No release commit hash is invented here; the same
Goal stays active until that receipt is verified. Earlier pending annotations
remain verbatim historical observations.

HYG-P716-S02-LEGACY-SPEC-WORKDIR, HYG-P716-S03-MODEL-SIZE and
HYG-P716-FULL-MACOS-SAY-WORKDIR remain accepted nonblocking maintenance in
the [canonical Hygiene journal](../journal/2026/10/2026-10-10-phase-71.6-hygiene-follow-up.md);
resolved HYG-P716-S01-RR01-HEADER history is retained. Existing accepted native
media and current hearing supply representative execution; no rerender or
playback is claimed by this projection. All `[>]` items, repository-full SBT,
remaining native combinations and timed benchmark remain deferred-not-run to
PHASE-71.7, which is planned and unstarted.
