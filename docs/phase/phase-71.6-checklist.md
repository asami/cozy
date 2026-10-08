# Phase 71.6 Checklist: Per-Tool Execution and Video Encoder Selection

Phase status: planned / not started
Ledger for: [Phase 71.6](phase-71.6.md)
Repository-full validation: deferred-not-run to PHASE-71.7

Completion is feature implementation, passing Executable Specifications and
actual representative mixed/software then selected hardware execution, review
and commits. `[>]` marks remaining integration/benchmark work transferred to
71.7, not a pass or a Phase 71.6 exit condition.

This checklist records planned work only. No implementation, test, benchmark,
review, commit or Phase start is claimed by its creation.

## Entry

Execution mapping (2026-10-08): the [plan](phase-71.6.md#execution-plan--2026-10-08)
connects P716-01 settings to actual producer commands, then runs Docker frames
through host software encoding and final audible playback before adding actual
VideoToolbox execution and remaining configuration branches. Write matching
Specs with the connected implementation and retain normal review/release.
P716-04 performance comparison remains transferred to P717-03.

- [ ] Consume Phase 71.5 closure and the accepted video-ending behavior.
- [ ] Select exact implementation files from the current tree; preserve unrelated
      work. Reuse the accepted 71.4/71.5 representative input and baseline.
- [ ] Identify installed tools and host/Docker/hardware prerequisites for the
      selected main routes here; remaining benchmark cases belong to P717-03.
      Do not select a different environment automatically.

## P716-01: Per-tool operating configuration

- [ ] Define design/specification and Executable Specifications for independent
      tool selections with Docker defaults and `~/.cozy/config.*` settings.
- [ ] Inventory Remotion, FFmpeg, ffprobe and other participating tool calls.
- [ ] Define keys, executable/image settings and precedence, including existing
      CLI/project options and global `--tool-mode` compatibility.
- [ ] Keep local hardware/executable settings separate from portable content.
- [ ] Expose effective per-tool settings and diagnose contradictory inputs.

## P716-02: Encoder invocation

- [ ] Retain explicit software H.264 and implement host VideoToolbox H.264.
- [ ] Pass selected executable, encoder and supported quality controls to the
      actual encoder, including renderer-owned invocation paths.
- [ ] Reject unsupported selection or hardware initialization failure without
      silent software fallback.
- [ ] Verify actual selected hardware execution using invocation/results and
      available diagnostics here; do not equate encoder availability with
      successful use. Missing prerequisites leave this outcome open.

## P716-03: Mixed pipeline

- [ ] Implement Docker Remotion rendering with host software encoding and run
      the normal Cozy entry through final video and audible playback before
      expanding encoder/configuration branches.
- [ ] Implement Docker Remotion rendering with host hardware encoding.
- [ ] Specify and exercise frame/audio handoff and host/container path mapping.
- [ ] Preserve existing Docker/software and complete host paths.
- [ ] Pass corresponding Executable Specifications for frame counts, narration
      coverage and timing; actual representative software/hardware final-card and
      audible player EOF acceptance also passes here. Remaining coverage is P717-01/03.
- [ ] Use ordinary temporary files and error reporting without new protection
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

- [ ] Focused Executable Specifications pass for selection, defaults,
      precedence, command construction, failures and media behavior.
- [ ] Preserve the accepted baseline and record actual representative mixed/
      software and selected hardware results, commands and playback outcomes.
      Only remaining combinations and performance comparison move to P717-03.
- [ ] Independent Phase review is complete and actionable findings are resolved.
- [>] Repository-full Cozy SBT validation is consolidated into P717-04.
- [ ] Complete the normal distinct Phase release commit and update the ledger.
- [ ] Preserve earlier Phase history and record the current aggregate owner as
      Phase 71.7 without rewriting past results.
