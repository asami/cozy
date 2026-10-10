# Phase 71.7 Checklist

Phase status: IN-PROGRESS
Updated: 2026-10-11
Ledger for: [Phase 71.7](phase-71.7.md)
Primary owner: Cozy
Repository-full validation: aggregate final owner for PHASE-71 through PHASE-71.7

This ledger receives remaining integration breadth, comparison and aggregate
validation; none is claimed as passed. Reuse applicable actual main-route
results from 71.4–71.6; first generation/playback is not deferred to this Phase.

Execution mapping (2026-10-08): follow the
[plan](phase-71.7.md#execution-plan--2026-10-08). Consume the working tool/player
routes, complete remaining P717-01/02 scenarios, run P717-03's fixed matrix,
then P717-04 aggregate validation/review/release. Reuse applicable narration,
fixtures and results across observations without substituting old timings or
unexecuted cases. No benchmark-cell review, new whole-repository removal audit
or additional acceptance ID is required. Existing states remain unchanged.

## P717-01: Actual video and audio acceptance

Stage Status:
- Current status: CLOSED
- Owner: Cozy
- Update rule: close against the following actual media outcomes

Execution note (2026-10-11): fresh EN preparation, corrected inspect, render and
build exited 0. Both direct/final full AV decodes exited 0: 34 WAV scenes, 7177
frames at 18 fps, 1280 × 720, H.264/AAC with 48 kHz stereo audio; each presented
AV end difference is 1/72000 second, below one 48 kHz sample. All 36 opaque card
frames are nonblack and the complete end card was viewed. Actual zero-start,
unmuted volume-1/rate-1 natural EOF at 398.722222 seconds and owned cleanup are
recorded with readiness/clients/cleanup true and terminal exit 0. The current
EN human reply “自然に聞こえている” is separately attributed.

Prior accepted JA 34-scene/8091-frame/449.5-second/36-card-frame evidence with
zero AV end difference, audible natural EOF/cleanup and “自然に聞こえた” is reused
at its existing scope. Phase 71.6 validation (nine suites, 233/233) is separately
reused after ordinary product/build-input inspection; no new SBT run is claimed.
The initial preparation/pending state and missing-profile inspect exit 1 remain
history; bounded unchanged credit-profile copying enabled corrected inspect 0.
No shared skill or production Scala change was made. Independent ordinary Step
review by `/root/phase717_s01_review01` sealed PASS, with no blockers, Hygiene
or Development Candidates. The parent recorded and read back the review in
the original typed ledger at revision 2; original-base Step commit entry was
accepted. This closure projection is included in the exact local P717-01
acceptance commit. The Phase remains IN-PROGRESS. P717-02..04 and the full Cozy
suite at release remain open. See the [execution journal](../journal/2026/10/2026-10-11-phase-71.7-media-acceptance.md).

- [x] JA/EN representative generation, direct render and final assembly preserve narration and the complete opaque end card through the common media end; reuse relevant existing results.
- [x] Applicable prior actual listening/player EOF and remaining locale/player observations confirm the expected ending. Recheck affected behavior; tool/file-open preparation alone is not acceptance.

## P717-02: Combined media, site and packaging acceptance

Stage Status:
- Current status: OPEN
- Owner: Cozy
- Update rule: close against the selected existing integration scenarios

- [ ] Real Core/intermediate/Storyboard/reuse, article-only/video-only complete-site and PDF scenarios pass on the combined tree, including relevant Phase 75 integration paths.
- [ ] Historical non-hash data/scripts, selected assets/settings and ordinary failure behavior work without reinstating removed protection guarantees.
- [ ] Representative generation/adoption, task-private publication and CAR/SAR packaging preserve their product semantics; no production deployment is performed.
- [ ] Explicit 2026-10-08 addition: SmartDox emits width-bounded, wrapping LaTeX table columns; focused executable specifications cover long Japanese/English four-column content and multi-page tables without deleting content or columns.
- [ ] Cozy's normal LaTeX PDF route consumes the corrected SmartDox dependency and confirms the selected table inputs fit the text area. Record SmartDox specification results separately from Cozy's aggregate suite; the immediate NICT wording workaround is not acceptance of this fix.

## P717-03: Native execution and benchmark

Stage Status:
- Current status: OPEN
- Owner: Cozy
- Update rule: close against the transferred P716-04 procedure

- [ ] Select the representative inputs, machine/tool versions and effective settings; record unsupported cases and missing prerequisites honestly.
- [ ] Exercise the plan's Docker baseline, mixed software/hardware and supported native cases, confirming actual encoder use and media behavior.
- [ ] Record separate render/encode/assembly/total times, cold startup and three warmed runs with medians, output size and visual quality; no speedup threshold is invented.

## P717-04: Aggregate validation and release

Stage Status:
- Current status: OPEN
- Owner: Cozy
- Update rule: close only with actual aggregate results and completed review/commit

- [ ] Earlier development closures and this explicit transfer are recorded without rewriting historical results or reopening completed Phases.
- [ ] One final repository-full Cozy SBT suite passes through serialized execution on the integrated tree.
- [ ] Independent review is complete and defects blocking the finite selected acceptance scenarios are resolved; unrelated new work is recorded separately.
- [ ] The distinct Phase release commit records integrated acceptance and the actual aggregate validation disposition.
