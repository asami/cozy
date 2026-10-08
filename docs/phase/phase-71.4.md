# Phase 71.4: Video Ending and Narration Repair

status=in-progress
split_full_test_policy=final-only
split_full_validation_method=sbt-full-suite
split_validation_bootstrap=none
validation_ownership=aggregate-deferred
aggregate_validation_owner=PHASE-71.7
aggregate_validation_sequence=["PHASE-71","PHASE-71.1","PHASE-71.2","PHASE-71.3","PHASE-71.4","PHASE-71.5","PHASE-71.6","PHASE-71.7"]
depends_on=phase-71.3.md
execution_priority=resumed_ja_representative

Status: in-progress; internal Slices P710-F04-S01 and P710-F04-S02 accepted by focused independent review; P710-F04-S03 bounded JA/EN native collection retained; Step/Phase closure remains open under the revised scope below
Planned at: 2026-09-29
Development item: DEV-034
Primary owner: Cozy
Split from [Phase 71](phase-71.md).
Predecessor: [Phase 71.3](phase-71.3.md).
Current execution state (2026-10-08): the same Goal is active after the
user-authorized resume. The S07 pause and selected
[75](phase-75.md) -> [75.1](phase-75.1.md) site-recovery priority remain historical.
The current Slice is P710-F04-S04-JA-REPRESENTATIVE; its new JA render and build
completed with exit 0. Native inspection records 34-scene WAV coverage, direct/final
8091 frames at 18 fps, equal 449.5-second presented ends, successful full decode
and each complete 36-frame nonblack final card. New JA final browser EOF passed
from start at 449.5 seconds with no browser error and a readable opaque final
card; the owned player completed and cleaned up. Current JA hearing is accepted
from the user's report, 「・」なしでスムーズに聞こえた: smooth narration without
spoken middle-dot. The human report is separate from native 34-scene coverage
and full browser EOF. Implementation/validation evidence is collected; parent
whole-F04 readiness passed. Independent protected-focused F04 Step review
`9ffdcb5a-5931-432b-bd37-a63ccd9738bc@1` passed over the complete owned result
and affected consumers. Final documentation preflight checked 67 links.
Existing nonblocking `HYG-714-S02-001` is preserved; the existing 203 passing
tests are reused after actual affected-input reconciliation. Independent Step
review is accepted; the exact Step commit and Phase closure remain OPEN.
The latest active-Goal observation and human report are linked in the resumed
JA journal. Phase 71.5 remains the structural successor after closure.
Former numeric successor: [Phase 72](phase-72.md), unchanged and not started.

## Goal

Fix the reported black video ending without losing narration. First pass one
real representative video through the normal Cozy generation and final-build
route and actual audible playback through EOF. Complete corresponding Specs,
review and commits. Broader media/site/packaging cases and repository-full
validation remain in [Phase 71.7](phase-71.7.md).

## Execution plan — 2026-10-08

Historically, the owning task stopped at S07 acceptance with its Goal paused. S08 Document
Project removal is frozen-not-started, not the next automatic 71.4 implementation.
The current scope allocation below takes precedence over the old handoff's
S08/global-audit/full-suite next steps. The actual same-Goal resume is now recorded
in the [resumed JA representative journal](../journal/2026/10/2026-10-08-phase-71.4-resumed-ja-representative.md).
Accepted aggregate entry `959d9d2d-c460-4bf6-a1c6-cf3bc414b322@2` names PHASE-71.7.
The original Goal binding/base/PLAN epoch and ledger
`cef8b372-0fa0-4831-8968-74962c12ccb5@17` retains 15 reviews and the factual
M0 status amendment, zero full
Phase reviews, F04 repair cycle 5/nonconverging 0, Integration repair cycle
7/nonconverging 0 and Phase repair cycle 0. The first factual pass recorded no
new execution. The later parent-admitted render/build and native inspection
results and accepted whole-F04 Step review are now linked in the journal.
The exact Step commit and mandatory full Phase review/release remain OPEN.

1. Select the existing JA VOICEVOX 34-scene Model Harness project and its normal
   Cozy entry, task-private outputs and an available audible playback route.
   Reuse accepted S01/S02 diagnosis and S03 inputs/narration/assets. This is a
   real representative, not a shortened new fixture or synthetic command receipt.
   Determine once which prior generated results remain applicable and what
   current generation/final assembly must run. A command/player availability
   check alone is not acceptance, and unavailable playback stays explicitly open.
2. Run that connected route now, inspect the actual final video, and listen
   through its end. Correct only observed generation/narration/ending defects,
   together with directly affected consumers and Specs, then run the affected
   route again. Preserve fractional-frame/AAC/final-assembly semantics and
   confirm no narration loss, black tail or missing opaque end card. Reuse
   applicable real results; do not repeat synthesis merely to prepare another
   check or expand into all locale/encoder combinations before this route works.
3. After that representative works, finish any remaining bounded video/spec
   obligations and affected regression checks. Retained accepted removals/site
   changes keep their scope; S08 removal and broader integration stay later.
   A remaining main-route defect belongs here, not in a 71.7 handoff.
4. Complete required Step acceptance/commit, whole-Phase review and distinct
   release commit for the revised scope. An already accepted Slice does not
   automatically satisfy whole-Step acceptance, but also needs no new isolated
   review solely because its evidence is being handed over. Report repository-full
   validation as deferred-not-run to 71.7. Record the actual working command,
   inputs/settings, final media and playback outcome for 71.5 to reuse. No
   per-playback review or new status/proof infrastructure is required.

Explicit handoff: unstarted S08 and remaining protection inventory -> P715-01/02/03;
remaining locale/listening/player coverage -> P717-01; combined site/packaging ->
P717-02; aggregate suite -> P717-04. S08's old D06 distribution-integrity retention
instruction is superseded by 71.5's no-exception removal policy. Preserve its
historical manifest, but do not execute it unchanged or reset its history.
Use the current [checklist](phase-71.4-checklist.md), not the old broad integration
obligation, to determine whether 71.4 can close. No checkbox is completed here.

This 2026-10-08 instruction supersedes the earlier blanket native-acceptance
deferral: the representative main route and its actual listening/EOF belong
to P710-F04 here. P717-01 retains the remaining breadth and affected combined-
tree rechecks. No old result is promoted to an unobserved listening PASS.

## Completion scope revision (2026-10-05)

Historical scope allocation: the 2026-10-08 plan above restores representative
native execution/listening to 71.4; only broader integration and full SBT stay deferred.

The user moved unfinished management-hash removal and remaining-removal
investigation to [Phase 71.5](phase-71.5.md). Keep all removals already made and
their actual validation/review results; do not revert or repeat them. Validate
retained changes as needed for their actual effect, but completing removal or
proving that no management hash remains is not a Phase 71.4 exit condition.
At that date, P710-04-INTEGRATION, native listening/player acceptance and the
aggregate full suite were transferred to Phase 71.7, without claiming a pass.
The current plan above brings representative generation/listening back here. Preserve
the Goal, original base, PLAN epoch, review history and repair counts. This is a
planning change only; it does not close the Phase or change live task state.

## Pause direction (2026-10-05)

The user selected a pause at a coherent current-unit boundary to prioritize
Phase 75. Let any running command finish, complete the current implementation
unit's validation and independent review, then preserve the current Goal/base/
PLAN epoch, dirty work, accepted results, review/repair history and exact remaining
obligations in the owning task. Do not start another implementation unit or
whole-Phase closure before this pause. Record the actual handoff and runtime
pause there; this document does not claim either has happened. This is not
Phase completion. The later completion-scope revision above transfers the
aggregate suite to Phase 71.7 without waiving it. After Phase 75, resume only
the remaining Phase 71.4 implementation/specification closure work.
Phase 75 first preserves historical/current artifacts, then handles canonical
deployment, article links and page ownership. The later statements about 71.5
describe its dependency after eventual closure, not the next active priority.

2026-10-06 split-reference correction: [Phase 75](phase-75.md) retains
preservation/canonical deployment; [Phase 75.1](phase-75.1.md) owns the moved
article-link and page-recovery work. The selected site-recovery priority now
covers 75 → 75.1 before resuming this Phase. The historical pause direction,
this Phase's technical scope and the 71.7 aggregate ownership are unchanged.

## Scope and owned Steps

- P710-F04: complete the diagnosed frame/audio/container correction and its
  Executable Specifications. Reuse the recorded diagnosis and accepted S01/S02
  results where applicable; do not restart diagnosis without a concrete defect.
- Keep an opaque end card through the common media end without clipping
  narration or appending black frames. Specifications cover fractional-frame
  duration, AAC padding, direct render and final assembly.
- Keep already implemented removal and site fixes; preserve their accepted
  results. Broader integration acceptance is owned by Phase 71.7; the current
  representative route must work before this Phase closes.
- Transfer P710-04-INTEGRATION's media/site workflows, historical non-hash
  data, scripts/assets/settings and ordinary failure checks to P717-01/02;
  transfer its remaining-removal audit to P715-01/06. No removal audit or broad
  native integration matrix is required to close this Phase. The representative
  native generation/playback in P710-F04 is required here.

## Phase Plan Gate

Phase Plan Gate: PROCEED

The estimates below record the original 2026-09-29 scope, not a new runtime
budget or permission to expand the revised completion conditions.

- target: expected duration centered on 6h; allowed ceiling 8h
- estimate_calibration: no directly comparable measured evidence; scope-based planning, low confidence; no model-speed multiplier
- planning_demand: open-ended-discovery
- recommended_parent_profile: gpt-5.6-sol / high
- profile_cost_role: expensive reasoning kernel
- expensive_reasoning_kernel: Decoded-frame/render/mux/player media-end root cause
- frozen_profile_transition_handoff: accepted Phase 71.3 contract and handoff described below
- parent_reasoning_mode_policy: standard
- estimated_at_recommended_profile: expected 6h; range 5–9h; 0h above target, fits expected <=8h ceiling; upper range is risk evidence only
- incoming_semantic_handoffs: []
- merge_attempts_for_every_sub_4h_child: none; no sub-four-hour expected member
- rebalance_attempts_for_every_sub_5h_child: none; no sub-five-hour expected member
- adjacent_merge_structural_rejection_evidence: no structural/authority rejection claimed; adjacent expected totals 14h/13h/12h/12h exceed ceiling
- profile_cost_only_rejection_forbidden: true
- short_child_basis: none
- overhead_tradeoff: independent closure/handoff/review/release overhead included; final-only avoids repeated full suites, with no invented numerical or monetary saving
- agent_reasoning_mode_policy: default standard; pro only at an eligible supported agent launch with frozen quality-first justification
- runtime_suitability: re-evaluate in the Phase execution task; this structural gate does not attest a future runtime
- source: applied split from Phase 71 on 2026-09-29

## Frozen handoff and independent closure

Consume the immediate predecessor's accepted closure and frozen handoff; do
not repeat its semantic discovery or claim its Step implementation as new work.

Hand over the implemented video correction, passing Executable Specifications,
retained removal work and the working representative route with actual playback
observations. No successor starts
automatically. Phase 71.5 owns unfinished management-hash and artifact-protection
removal. Phase 71.7 owns remaining combined-tree integration and the one aggregate full
suite for Phase 71 through 71.7; neither is a Phase 71.4 closure prerequisite.

No genuine incoming semantic authority/human/external-operation/release/migration
edge is asserted: `incoming_semantic_handoffs=[]`. These are serial delivery
and contract dependencies, not fabricated semantic gates. Close only this unit's owned checklist outcomes; integration rechecks do not reopen predecessors.
Routine execution included in a Sol/high container is limited to implementing
and proving that container's discovered contract or root-cause correction;
settled document/media and site/PDF work is assigned to Terra/high successors.

## Validation and closure boundary

Execution update, 2026-10-05: internal S01 actual-WAV coverage acceptance is
retained. S02 focused independent review `05ffd477-bf4b-47ec-97fb-c3f84e602712@1`
is PASS after 203/203 current focused and consumer scenarios, including closure
of `CPB-714-S02-001`. Original ledger
`cef8b372-0fa0-4831-8968-74962c12ccb5@5` retains cumulative Step repair cycle 5
and nonconverging count 0; neither history nor acceptance scope is reset.
The [AAC/container diagnosis](../journal/2026/10/2026-10-05-phase-71.4-aac-container-diagnosis.md)
retains the failed native results and rejected two-part `copyts` shortcut.
S01/S02 evidence supplies internal Slice acceptance only. Of the six native JA
VOICEVOX/EN macOS-say operations in the
[Model Harness acceptance record](../journal/2026/10/2026-10-05-phase-71.4-model-harness-provider-acceptance.md)
all six JA/EN synthesis/render/build commands completed with actual exit 0 after
task-private preparation. Both locales have ordered source-matching 34-scene
WAVs covered by generated integer windows. Direct/final full decodes match props:
JA 8091 frames at 18 fps, equal 449.5-second presented ends; EN 7177 frames,
absolute audio/video end difference 1/72000 second, below one 48k sample. Both
locales retain the complete decoded 36-frame opaque final card. Bounded S03
implementation evidence collection is complete, with no independent S03 or
whole-Step acceptance inferred. Historical Chrome observation subsequently
recorded all four JA/EN direct/final movies played from start to actual EOF:
`ended=true`, `error=null`, current time equal to duration (JA 449.5 seconds;
EN 398.722222 seconds). All four were muted; listening remains unverified.
The earlier queued JA file opening and CUA inventory timeout remain failed
player routes in history, not the current EOF projection. See the
[resumed JA representative journal](../journal/2026/10/2026-10-08-phase-71.4-resumed-ja-representative.md)
for the observation source. Under the 2026-10-08 plan, Phase 71.4 selects and completes the
representative player/listening route; 71.7 owns the remaining coverage.
No new SBT is required for this documentation-only
preparation; unchanged product code retains current 203/203 and accepted S01/S02
evidence. The resumed JA final browser EOF is now recorded in the resumed
representative journal; the current JA hearing condition is accepted from the
separately attributed human report. Independent Step/Phase acceptance remains open;
remaining integration breadth and final full validation stay in Phase 71.7.
Phase 71.4's implementation/specification and representative-route acceptance
and its corresponding commits remain open; no completed status is inferred.

[Phase 71.4 Checklist](phase-71.4-checklist.md) is the completion ledger.
Every owned Step/Slice needs executable focused validation and appropriate
compile/consumer coverage, independent acceptance review, exact Step commits,
closure journals/ledger, mandatory full Phase review and a distinct release
commit. No item is implemented or accepted by this planning split.

Repository-full validation remains deferred-not-run, now owned by PHASE-71.7.
The current Phase declarations record the transfer; original committed
handoffs naming PHASE-71.4 remain historical evidence and are not rewritten.
Ordinary Git changes, commands/results and review records are sufficient;
do not introduce hashes, comparison copies or unchanged-input proofs.

The repository already has `build.sbt`, so `split_validation_bootstrap=none`.
The first delivery's real media driver is product acceptance, not permission
to invent `bootstrap-structural` or waive that product proof. Entry seals the
live reciprocal planning chain; no prerequisite planning commit is required.

## Non-goals and ownership

Only Cozy is the mutation repository. SimpleModeling.org is a real acceptance
driver on a task-private copy, not permission to modify its production source,
media, scripts or history. External project declarations or authoring skills,
if actually required, need separate exact implementation authority; do not
silently replace the real route with a Cozy-only invented fixture. Skill
modification is not authorized by this split. No publish, upload, deployment,
remote write, human-approval workflow, speculative metadata/receipt framework,
CNCF runtime work or successor execution is included.

Use the [file-update/digest boundary](../spec/file-update-management.md).
Retain the already implemented removals. Further management-hash and protection
removal belongs to Phase 71.5. Preserve ordinary collection hashing, non-hash
data, safe paths and real artifact validity; do not add protection mechanisms.
Meaningful DSLs remain Codex-authored through declared producers; Cozy does not
invent semantics or `touch` sources to pretend they were regenerated.

Closed Phase 30/60/60.1 and Phase 61/61.1 remain baselines, not reopened work.
Later delivery scope belongs to its own Phase. Immediate caller reconciliation
necessary for a compile-valid change stays with that change, but does not claim
a successor's product outcome or move its canonical checklist obligation.

## References

- [File update management](../spec/file-update-management.md)
- [Core-rooted dependency detail](../notes/core-rooted-make-level-media-regeneration.md)
- [2026-09-28 hash-removal decision](../journal/2026/09/2026-09-28-phase-71-hash-removal-decision.md)
- [Current Storyboard specification](../spec/video-storyboard.md)
