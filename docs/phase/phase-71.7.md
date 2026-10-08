# Phase 71.7: Integrated Media Acceptance and Aggregate Validation

status=planned
split_full_test_policy=final-only
split_full_validation_method=sbt-full-suite
split_validation_bootstrap=none
validation_ownership=aggregate-final-owner
aggregate_validation_owner=PHASE-71.7
aggregate_validation_sequence=["PHASE-71","PHASE-71.1","PHASE-71.2","PHASE-71.3","PHASE-71.4","PHASE-71.5","PHASE-71.6","PHASE-71.7"]
depends_on=phase-71.6.md
execution_priority=after_phase_71.6_closure

Status: planned; no execution or acceptance claimed
Planned at: 2026-10-05
Primary owner: Cozy
Predecessor: [Phase 71.6](phase-71.6.md).
Completion ledger: [Phase 71.7 Checklist](phase-71.7-checklist.md).

## Goal and ownership

Complete remaining real-media/site/packaging coverage, native encoder comparison
and one repository-full SBT suite after the main routes actually work in
71.4–71.6. This is not the first connection or first playback of those routes.
The user selected Phase 75 first; after its 2026-10-06 split, that priority
covers [75](phase-75.md) -> [75.1](phase-75.1.md), then
71.4 -> 71.5 -> 71.6 -> 71.7. Both site-recovery children keep their own
release/full validation and are not members of this aggregate chain.
This planning update does not start a Phase, pause a task or mutate any Goal.

| Source obligation | Receiving Step |
| --- | --- |
| P710-F04-S03 remaining locale/listening/player coverage after the 71.4 representative route | P717-01 |
| P710-04-INTEGRATION remaining media/site acceptance and ordinary failure scenarios | P717-01/02 |
| P715-06 remaining generation/adoption, task-private publication and CAR/SAR packaging coverage | P717-02 |
| P716-04 benchmark and remaining native combinations; representative mixed/software and hardware execution belong to 71.6 | P717-03 |
| RELEASE-71.4-VALIDATION aggregate suite and former 71.5/71.6 standalone full suites | P717-04, one consolidated suite |

Unfinished management-hash/protection removal belongs to Phase 71.5, not a new
Phase 71.7 removal audit. Retain all earlier implementation, commands/results,
reviews and failures. Original handoffs naming 71.4 remain historical; current
Phase declarations transfer the still deferred-not-run full-suite obligation.
Do not rewrite accepted results or restart completed Phases.

## Execution plan — 2026-10-08

Use the transferred finite scenarios below, not a new system-wide audit. Keep
P717-01..04 as the acceptance IDs and record actual evidence in their checklist.

1. **Start from the working routes and select remaining cases.** Consume the
   71.4 representative JA route, 71.5 post-removal result and 71.6 actual mixed/
   hardware results, including their commands and playback observations. Extend
   the existing JA/EN Model Harness inputs and task-owned outputs only for the
   remaining declared cases. Reconcile actual tool versions/configuration, media,
   decodes and later player observations by their actual scope; muted playback
   is not listening, and a queued file-open is not player acceptance. Report an
   unavailable prerequisite once with its remaining obligation rather than
   looping on UI setup or claiming success. Reuse applicable pre-generated
   narration/assets for the benchmark; do not synthesize them for every encoder.
2. **Complete functional media/site/packaging acceptance (P717-01/02).** Use the
   shared private drivers for the required Core/intermediate/Storyboard/reuse,
   PDF and article/video site cases. Consume actual 75/75.1 results and recheck
   affected integration, without repeating historical artifact preservation or
   public deployment. Finish remaining listening/player EOF evidence; do not repeat applicable
   accepted observations merely because their owning Phase changed. Fix concrete
   integration defects here with affected specs; do not reopen closed children
   or start another full protection-removal search.
3. **Run the fixed comparison (P717-03).** Reuse applicable functional baseline/
   mixed results and establish remaining cases before timing the existing
   five-case matrix. Keep three warmed
   runs per supported case, cold startup separately, and the specified stage/
   total/quality measurements. Do not run competing benchmarks concurrently.
   Reuse an actual run for multiple observations when it supplies all required
   evidence; do not substitute old timings for a newly changed command or remove
   required end-to-end/native checks to save time. Unsupported/missing cases
   remain explicitly unaccepted; no speedup target is invented.
4. **Validate the assembled tree and close (P717-04).** After required functional
   fixes, run the one consolidated repository-full SBT obligation, complete
   normal independent review and release. An observed failure or relevant
   later fix still needs appropriate revalidation; "one suite" does not mean
   failures may be ignored or a changed final tree inherits an unrelated PASS.
   Preserve earlier deferred-not-run records and report the actual final result.

Do not add an independent review after every playback or benchmark cell.
Retain existing Step/Phase acceptance and commit obligations; the grouped
procedure changes work order, not acceptance authority. No extra scenario,
comparison ledger, content-copy proof or runtime measurement service is needed.

## P717-01: Actual video and audio acceptance

Use the existing representative JA VOICEVOX / EN macOS-say 34-scene Model Harness
scenario. Preserve earlier synthesis/render/build and decoded-frame results;
repeat only where later changes affect them. Consume the accepted representative
listening/EOF results from 71.4–71.6 and complete remaining locale/player
observations. Narration must remain audible through its intended
ending, with the opaque end card covering the common media end and no appended
black frames. Check direct render and final assembly, fractional-frame timing
and AAC/container behavior against the implemented specification. Do not count
queued file opening or tool startup failure as successful player acceptance.

## P717-02: Combined media, site and packaging acceptance

Use the already selected real Core/intermediate/Storyboard/reuse driver,
article-only and video-only complete-site scenarios, and selected PDF inputs.
Confirm historical non-hash data and scripts, current assets, renderer/narration
settings and useful ordinary failures under the post-71.5 contract. Do not
require removed protection, rollback or unchanged-artifact guarantees.
Include representative local generation/adoption, task-private publication and
CAR/SAR packaging from the existing removal work list. Consume the completed
[Phase 75 placement](phase-75.md) and [75.1 link/homepage](phase-75.1.md)
work; recheck affected integration paths, not their entire accepted releases. Do not upload or republish real content here.

### SmartDox LaTeX table wrapping — explicit scope addition (2026-10-08)

The user assigned the observed PDF table overflow to P717-02. SmartDox owns
the converter implementation and its focused executable specifications; Cozy
owns consumption of the corrected dependency and normal PDF-route acceptance.
This is planned work, not an implemented or accepted fix, and does not reopen
Phase 71.3 or change the Phase execution order.

`smartdox/src/main/scala/org/smartdox/converters/Dox2LatexConverter.scala`
currently emits natural-width `l` columns for `longtable`. Long cell text can
therefore extend beyond the page. Emit wrapping columns with widths bounded by
the available line width, including column padding and rules, while preserving
table content, column count and multi-page table behavior.

Cover a four-column table containing long Japanese and English text and a
multi-page table in SmartDox's focused executable specifications. Confirm an
actual PDF through Cozy's normal LaTeX renderer using the corrected SmartDox
dependency. Cozy's aggregate suite does not substitute for those SmartDox
specifications. Do not switch to Asciidoctor, delete columns, or patch generated
TeX/PDF as the implementation of this fix.

For the immediate NICT feedback document, shorten figure/table wording as a
content-side workaround requested by the user. That document workaround does
not complete this development item. No SmartDox source modification is part of
the immediate document-editing task.

## P717-03: Native execution and benchmark

This is the existing P716-04 comparison, without expanding its matrix.
Representative mixed/software and hardware operation already belongs to 71.6;
reuse its applicable functional evidence, then complete remaining combinations
and the actual measurements below.

Use one representative storyboard, pre-generated narration, assets, resolution,
fps and effects. Run the following matrix with distinct task-owned outputs:

| Case | Frame rendering | Encoding |
| --- | --- | --- |
| Baseline | Docker | Docker / software H.264 |
| Mixed software | Docker | Host / software H.264 |
| Mixed hardware | Docker | Host / VideoToolbox H.264 |
| Native software | Host | Host / software H.264 |
| Native hardware | Host | Host / VideoToolbox H.264 |

Measure frame rendering, encoding, final assembly and total elapsed time
separately; record intermediate I/O cost when material. Keep cold startup
separate and report the median of three warmed runs for each supported case.
Do not run benchmark workloads concurrently. Record machine/tool versions,
effective settings, output size and visual quality. Document quality settings;
equal CRF values are not a cross-encoder quality equivalence.

Include an end-to-end Cozy generation comparison, decoded frame and narration
coverage checks, and actual final-card/player behavior. Report unavailable
prerequisites or unsupported cases rather than inventing results. The baseline
and mixed paths remain required functionality; an unavailable machine is not
evidence that hardware acceptance passed. No minimum speedup is assumed, and a
slower measured result does not by itself fail functional acceptance.


## P717-04: Aggregate full validation and closure

Consume the accepted development outcomes of Phase 71 through 71.6 and their
existing history. Run one final repository-full Cozy SBT suite through the
normal serialized route on the integrated tree. Historical deferred results
stay deferred-not-run until the actual aggregate result exists; they are not
retroactively relabeled as full-suite passes. Complete normal independent
review and the distinct Phase release commit against the checklist.

## Bounded completion and ordinary evidence

At entry, select concrete inputs and expected outcomes for the scenarios above.
Do not add a new exhaustive audit, feature request or quality gate after each
passing run. Fix defects that actually prevent these outcomes within Phase
71.7, without reopening earlier Phases; record unrelated work separately.
Reuse relevant existing validation/review results using ordinary Git changes
and command/test logs. Revalidate affected behavior, not every accepted item.
Do not create management hashes, comparison copies, unchanged-input proofs or
new bookkeeping/verification infrastructure. This plan invents no new test
suite or workload beyond the selected scenarios and aggregate suite. The main
route observations moved earlier are shared evidence, not duplicate work.

## References

- [Phase 71.4](phase-71.4.md) — video implementation and retained evidence
- [Phase 71.5](phase-71.5.md) — removal and existing-specification acceptance
- [Phase 71.6](phase-71.6.md) — execution/encoder implementation
- [Scope decision](../journal/2026/10/2026-10-05-phase-71-completion-boundaries.md)
