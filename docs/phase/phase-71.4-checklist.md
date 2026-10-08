# Phase 71.4 Checklist

Phase status: IN-PROGRESS
Updated: 2026-10-08
Ledger for: [Phase 71.4](phase-71.4.md)
Primary owner: Cozy
Repository-full validation: deferred-not-run to PHASE-71.7

Implementation is in progress. These are the only OPEN obligations owned by this
Phase; the original P710-03/P710-04 labels are partitioned, not additional work.
No child execution or acceptance is implied by applying the planning split.

Current execution state (2026-10-08 resume): the same Goal is active.
The owning task's historical S07 pause remains recorded in the
[actual handoff](../journal/2026/10/2026-10-05-phase-71.4-pause-and-phase-75-priority.md).
The historical site-recovery priority was 75 -> 75.1 before authorized resumption.
Accepted aggregate entry `959d9d2d-c460-4bf6-a1c6-cf3bc414b322@2` names PHASE-71.7.
Ledger `cef8b372-0fa0-4831-8968-74962c12ccb5@17` preserves all 15 reviews
and the factual M0 status amendment,
zero full Phase reviews, F04 repair cycle 5/nonconverging 0, Integration repair
cycle 7/nonconverging 0 and Phase repair cycle 0. The original Goal binding,
base and PLAN epoch remain. P710-F04-S04-JA-REPRESENTATIVE's new JA render/build
completed with exit 0. Native inspection records 34-scene WAV coverage,
direct/final 8091 frames at 18 fps with equal 449.5-second presented ends,
successful full audio/video decode and each complete 36-frame nonblack final
card. The parent visually observed the readable opaque next-episode card.
New JA final browser EOF passed from start at 449.5 seconds with no browser
error and a readable opaque final card. The owned player completed and cleaned
up. The current JA hearing condition is accepted from the user's exact report,
「・」なしでスムーズに聞こえた: smooth narration without spoken middle-dot.
This human report is separate from native scene coverage and browser EOF.
Implementation/validation evidence is collected; parent whole-F04 readiness
passed. Independent protected-focused whole-F04 Step review
`9ffdcb5a-5931-432b-bd37-a63ccd9738bc@1` passed over 20 owned and six affected
paths. Final documentation preflight checked 67 links. Existing
nonblocking `HYG-714-S02-001` is preserved, and the existing 203 passing tests
are reused after actual affected-input reconciliation. Independent Step review
is accepted; the exact Step commit and Phase release remain OPEN. See the
[resumed JA representative journal](../journal/2026/10/2026-10-08-phase-71.4-resumed-ja-representative.md).
Follow the [current execution plan](phase-71.4.md#execution-plan--2026-10-08):
first run the existing JA 34-scene route through normal generation, final build
and audible playback to EOF; repair observed main-route defects with their
Specs, then finish the bounded remaining video scope and existing acceptance.
Unstarted S08 belongs to P715-01/02/03, not another 71.4 Slice. Remaining integration breadth and
full validation remain P717 obligations. No item is marked passed by this update.

Structural successor after eventual closure: [Phase 71.5](phase-71.5.md).
Its removal work is not a closure prerequisite of Phase 71.4. The user's
2026-10-08 revision adds actual representative generation/playback to the
bounded video-ending/narration implementation, passing Specs, review and commits.
Keep removals already made; unfinished removal/inventory belongs to Phase 71.5.
`[>]` means transferred, not passed and not a current Phase exit condition.
Existing evidence and all review/repair history remain.

## P710-F04: Non-black common media ending with all narration retained

Stage Status:

- Current status: IN-PROGRESS
- Owner: Cozy
- Update rule: close only against the following checklist and accepted executable evidence

- [ ] P710-F04-BEHAVIOR: The existing JA VOICEVOX 34-scene route works through normal Cozy generation and final assembly; actual audible playback through EOF confirms retained narration, no black tail and the complete opaque end card. Main-route failures are resolved here before branch expansion.
- [ ] P710-F04-SPEC: Affected subsystem specification and Given/When/Then executable specifications, including changed consumers and relevant failure behavior, are reconciled and focused validation succeeds.
- [ ] P710-F04-ACCEPT: Independent Step acceptance review and the exact Step acceptance commit bind this owned result without claiming successor implementation.

Internal Slice P710-F04-S01 is accepted by focused independent review
`63ad94ec-aa97-4d7c-b758-476b7f65a940@1`. Applicable focused evidence is
150 current passing scenarios plus 44 retained successes; the earlier failed
194-scenario invocation remains failed. These outcomes do not check any whole
Step item above. Internal Slice P710-F04-S02 is accepted by focused independent
review `05ffd477-bf4b-47ec-97fb-c3f84e602712@1` (PASS), with 203/203 current
focused and consumer scenarios and `CPB-714-S02-001` resolved. Original ledger
`cef8b372-0fa0-4831-8968-74962c12ccb5@5` retains cumulative Step repair cycle 5
and nonconverging count 0. Prior failed results and the rejected two-part
`copyts` shortcut remain in the
[diagnosis record](../journal/2026/10/2026-10-05-phase-71.4-aac-container-diagnosis.md).
Internal S01/S02 acceptance checks no whole Step or release box. P710-F04-S03
[native Model Harness acceptance preparation](../journal/2026/10/2026-10-05-phase-71.4-model-harness-provider-acceptance.md)
has completed bounded native collection: static preparation and all six actual
JA/EN synthesis/render/build terminal exits 0. Both locales have ordered
source-matching 34-scene WAVs covered by generated integer windows. Direct/final
full decodes match props: JA 8091 frames and equal 449.5-second presented ends;
EN 7177 frames and absolute presented-end difference 1/72000 second, below one
48k sample. Both locales have the whole decoded 36-frame opaque final card.
These are S03 implementation observations, without independent S03/Step
acceptance. A later historical Chrome observation recorded all four JA/EN
direct/final movies played from start to EOF with `ended=true`, `error=null`
and current time equal to duration (JA 449.5 seconds; EN 398.722222 seconds).
All four were muted, so listening remains unverified. The earlier queued file
opening and CUA inventory timeout remain failed player routes in history.
The observation source is linked in the resumed JA representative journal.
Phase 71.4 now owns the representative JA
player/listening route; Phase 71.7 owns remaining coverage. No whole-Step or release box is checked.
No new SBT is required for documentation-only preparation;
current 203/203 and accepted S01/S02 evidence are reused for unchanged product
code. Current JA hearing is now accepted from the separately attributed user
report linked in the resumed JA journal. The new final browser EOF and native
34-scene coverage remain separate evidence; the user did not explicitly enumerate
34 scenes or state 449.5 seconds. Historical muted-player listening remains
unverified. No whole-Step or release box is checked by this factual update.

## P710-04-INTEGRATION: Combined-tree acceptance, global inventory audit and aggregate full validation

Stage Status:

- Current status: TRANSFERRED
- Owner: Cozy; Phase 71.5 removal / Phase 71.7 integration
- Update rule: receiving checklists own the outcomes; transfer is not a passing result

- [>] P710-04-INTEGRATION-BEHAVIOR: Remaining removal/inventory is consolidated into P715-01/06; remaining combined-tree media/site acceptance into P717-01/02; representative P710-F04 acceptance stays here.
- [>] P710-04-INTEGRATION-SPEC: Keep implemented changes and passing specifications. Remaining removal uses P715-05 existing Specs; integration uses P717-01/02/04.
- [>] P710-04-INTEGRATION-ACCEPT: Keep accepted internal reviews. Receiving Phase review/commits close transferred work; no additional Integration Step commit is required for 71.4 closure.

## Closure

Stage Status:

- Current status: OPEN
- Owner: Cozy
- Update rule: close only when all owned Step items and the following release ledger are satisfied

- [ ] RELEASE-71.4-HANDOFF: Video implementation/Executable Spec Step and real representative route are accepted; hand over working commands, inputs/settings, final media and playback results. Preserve existing changes/results and send unfinished removal/remaining integration to 71.5/71.7.
- [ ] RELEASE-71.4-REVIEW: The mandatory Phase review and any bounded closure repair complete for this exact Phase tree; closure journals and checklist agree.
- [ ] RELEASE-71.4-VALIDATION: Corresponding video Executable Specifications pass; relevant existing results for retained changes are reused, with revalidation only for affected behavior. Actual representative generation/playback also passes. Removal completion, broad native integration and full SBT are not exit conditions.
- [>] RELEASE-71.4-AGGREGATE: Remaining broad native/aggregate obligations are consolidated into P717-01/02/04; full SBT remains deferred-not-run, not waived.
- [ ] RELEASE-71.4-COMMIT: The distinct Phase release commit includes its final closure ledger and correct aggregate validation disposition; no successor is started.
