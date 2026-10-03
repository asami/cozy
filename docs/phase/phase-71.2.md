# Phase 71.2: Video Hash Removal, Dialogue Continuity and Pronunciation

status=closed
split_full_test_policy=final-only
split_full_validation_method=sbt-full-suite
split_validation_bootstrap=none
validation_ownership=aggregate-deferred
aggregate_validation_owner=PHASE-71.4
aggregate_validation_sequence=["PHASE-71","PHASE-71.1","PHASE-71.2","PHASE-71.3","PHASE-71.4"]
depends_on=phase-71.1.md
execution_priority=current_video_generation_blocker

Status: closed

Acceptance evidence: P710-03-V-S01 focused validation (3 suites, 138 tests) and independent Slice review accepted; P710-03-V-S02 focused validation (5 suites, 188 tests) and independent Slice review accepted; P710-03-V-S03 focused validation (6 suites, 219 tests) and independent Slice review accepted; P710-03-V-S04 accepted after focused validation (11 suites, 297 tests) and independent Slice review 8235032a-399d-4ff9-b456-3cf30d373f22 revision 1, persisted review ledger revision 5; P710-03-V-S05 focused validation passed (15 suites, 331 tests); P710-03-V accepted with independent PASS Step review 2b86b3bc-da3b-42d7-a04e-fff37a230391 revision 1 and acceptance commit 00e73d99aaf486370032dc9036426a75559b303d. P710-F01 is accepted after faithful private input preparation and successful native inspect, synthesize, render, build and review-evidence (34 scenes, 41 evidence frames); parent observations cover all 17 exact final-MP4 boundaries, and P710-F01-S02 continuity specifications are authored. Fresh selected validation passed (5 suites, 170/170 tests: continuity 2/2 and four accumulator suites 168/168), including actual native rendering; failed, canceled, ignored, pending and aborted counts are zero, with no compiler warning lines. Native helper, SBT and wrapper exited 0 and the serial lock was released. Independent PASS complete-F01 Step review 22242814-2c16-418b-837d-39d24901e5ba revision 1 and exact acceptance commit 6511b6ef7ec3991aa6bb3c233d2ac90bab59c410 close F01. P710-F02 is accepted and CLOSED after actual focused validation (5 suites, 165/165 tests: compound 2/2; four accumulators 163/163), zero failed/canceled/ignored/pending/aborted, no compiler warning lines, helper/SBT/wrapper exit 0 and serial lock released. Both full-34-scene native passes retained 68 successful audio queries and 68 syntheses, preserved source/display/other JSON/speakers/tuning, and proved dictionary-only same-path regeneration with corrected nine-mora prefix and no internal pause. Parent WAV inspection is complete; the actual user's 2026-10-03 clarification confirms that U+30FB (`・`) was not pronounced in the corrected target WAV and that this satisfies the requirement to omit its pronunciation, resolving the earlier ambiguous message without extending it to a broader listening verdict. The corrected pass uses the authored dictionary mapping, preserves display text and does not enable global middle-dot removal. Independent PASS complete-F02 Step review 68b2ef62-714e-435a-8987-541059fc3dd1 revision 1 and exact acceptance commit 2c5e9402096528dc1ecd6558470e59ee7363016d bind F02 acceptance. All three F02 boxes are checked. The sole full Phase review completed with CPB-P71.2-001, resolved by the actual user clarification and accepted M0 documentary amendment. Phase 71.2 is CLOSED by the distinct local release containing the [normal closure record](../journal/2026/10/2026-10-03-phase-71.2-normal-closure.md). The sole independent full Phase review and its accepted M0 clarification amendment leave zero current blockers. Repository-full validation remains deferred-not-run to PHASE-71.4; no successor execution is claimed.
Planned at: 2026-09-29
Development item: DEV-034
Primary owner: Cozy
Split from [Phase 71](phase-71.md).
Predecessor: [Phase 71.1](phase-71.1.md).
Successor: [Phase 71.3](phase-71.3.md).

## Goal

Remove the remaining video-local non-integrity hashes and reconcile its scaffolds, source propagation and rendering/narration consumers with the accepted predecessor contracts. Prove same-visual speaker continuity and compound-term speech through actual selected generation.

## Scope and owned Steps

- P710-03-V: remove the remaining video renderer/narration/production/review identity computations and persisted fields identified by the Phase 71 inventory, reconciling all affected code, models, scaffolds and executable specifications. The predecessor's approval-prerequisite correction is accepted input, not work to repeat.
- P710-F01: reproduce the Model Harness review/static-check/direct-execution scene and other same-visual dialogue pairs. Determine authoring/scaffold propagation versus renderer cause before changing code. Reuse `visual.baseSceneId` with a fixed effects palette: heading, diagram, items and background remain continuous; speaker/caption/mouth may change. A new visual/section keeps its intended entrance.
- P710-F02: display `モデル・アップ・ダウン` unchanged and supply `モデルアップダウン` to synthesis. Reuse script `pronunciations`, Storyboard pronunciation notes and opt-in `voiceTextNormalization.removeMiddleDots`; no second normalizer or silent global punctuation removal.
- Check actual provider payload, pronunciation-setting-only audio regeneration and uninterrupted spoken output, not configuration alone. Preserve existing `parts[].script` projects and independently selectable final/confirmation generation.

## Phase Plan Gate

Phase Plan Gate: PROCEED

- target: expected duration centered on 6h; allowed ceiling 8h
- estimate_calibration: no directly comparable measured evidence; scope-based planning, low confidence; no model-speed multiplier
- planning_demand: open-ended-discovery
- recommended_parent_profile: gpt-5.6-sol / high
- profile_cost_role: expensive reasoning kernel
- expensive_reasoning_kernel: Unproved authoring/scaffold-versus-renderer continuity cause
- frozen_profile_transition_handoff: accepted Phase 71.1 contract and handoff described below
- parent_reasoning_mode_policy: standard
- estimated_at_recommended_profile: expected 6h; range 5–8h; 0h above target, fits expected <=8h ceiling; upper range is risk evidence only
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

Hash-free video configuration/scaffold/output contract, actual rendered/listened continuity and speech evidence, and stable render/narration inputs. Phase 71.3 consumes the video contract and Phase 71.4 consumes the stable pipeline for media-end diagnosis.

No genuine incoming semantic authority/human/external-operation/release/migration
edge is asserted: `incoming_semantic_handoffs=[]`. These are serial delivery
and contract dependencies, not fabricated semantic gates. Close only this unit's owned checklist outcomes; integration rechecks do not reopen predecessors.
Routine execution included in a Sol/high container is limited to implementing
and proving that container's discovered contract or root-cause correction;
settled document/media and site/PDF work is assigned to Terra/high successors.

## Validation and closure boundary

[Phase 71.2 Checklist](phase-71.2-checklist.md) is the completion ledger.
Every owned Step/Slice needs executable focused validation and appropriate
compile/consumer coverage, independent acceptance review, exact Step commits,
closure journals/ledger, mandatory full Phase review and a distinct release
commit. No item is implemented or accepted by this planning split.

Repository-full SBT validation alone is deferred to [Phase 71.4](phase-71.4.md)
under the reciprocal final-only chain. At release record focused assurance and
`repository_full_suite=deferred-not-run`; do not claim ordinary full-suite
assurance.

S05 removes only video descriptor/script digest emission and six unused video
digest imports, with written publication-bundle and copied-byte specifications
and a [purpose audit](../journal/2026/10/2026-10-02-phase-71.2-video-digest-boundary-audit.md).
ProducerSource source/currentness remains Phase 71.3-owned; named publication
artifact integrity remains unchanged. P710-03-V is accepted by independent PASS
Step review 2b86b3bc-da3b-42d7-a04e-fff37a230391 revision 1 and exact acceptance
commit 00e73d99aaf486370032dc9036426a75559b303d. F01 private input preparation
and successful native inspect, synthesize, render, build and review-evidence
(34 scenes, 41 evidence frames) are recorded in the [dialogue-continuity baseline](../journal/2026/10/2026-10-02-phase-71.2-dialogue-continuity-baseline.md).
Subsequent parent inspection covers all 17 exact previous-end/answer-first
final-MP4 boundaries: heading, diagram/items and backdrop remain continuous,
with no product correction selected. The earlier six sampled views remain
historical evidence. P710-F01-S02 adds the [continuity contract](../spec/video-dialogue-continuity.md),
[design](../design/video-dialogue-continuity.md) and [executable chapter](../../src/test/scala/cozy/video/CozyVideoDialogueContinuitySpec.scala).
Fresh selected validation passed: 5 suites, 170/170 tests (continuity 2/2;
four accumulator suites 168/168), including actual native rendering. Failed,
canceled, ignored, pending and aborted counts are zero; no compiler warning
lines were recorded. Native helper, SBT and wrapper exited 0 and the serial
lock was released. Independent PASS complete-F01 Step review
22242814-2c16-418b-837d-39d24901e5ba revision 1 and exact acceptance commit
6511b6ef7ec3991aa6bb3c233d2ac90bab59c410 close F01. F02 is accepted and CLOSED with
the [compound-narration contract](../spec/video-compound-narration.md),
[design](../design/video-compound-narration.md) and
[executable chapter](../../src/test/scala/cozy/video/CozyVideoCompoundNarrationSpec.scala)
authored. Actual focused validation passed (5 suites, 165/165 tests: compound
2/2; four accumulators 163/163), with zero failed/canceled/ignored/pending/
aborted and no compiler warning lines. Helper/SBT/wrapper exited 0 and the
serial lock was released. Both full-34-scene native passes completed with
68 successful audio queries and 68 syntheses, source/display/other JSON and
speakers/tuning preserved, and dictionary-only same-path audio regeneration.
The corrected query begins with the nine compound moras without an internal
pause; parent target/combined WAV inspection confirms positive, non-silent
normalized PCM. The actual user's 2026-10-03 clarification confirms that U+30FB
(`・`) was not pronounced in the corrected target WAV and that this satisfies
the requirement to omit its pronunciation. This resolves the earlier ambiguous
message without extending it to a broader listening verdict. The corrected pass uses the authored
dictionary mapping, preserves display text and does not enable global
middle-dot removal. Independent PASS complete-F02 Step review
68b2ef62-714e-435a-8987-541059fc3dd1 revision 1 and exact acceptance commit
2c5e9402096528dc1ecd6558470e59ee7363016d close F02 and its three checklist
items; see the [actual-results journal](../journal/2026/10/2026-10-03-phase-71.2-compound-narration.md).
Phase 71.2 is CLOSED by the distinct local release containing the [normal closure record](../journal/2026/10/2026-10-03-phase-71.2-normal-closure.md). The sole independent full Phase review and its accepted M0 clarification amendment leave zero current blockers. Repository-full validation remains deferred-not-run to PHASE-71.4; no successor execution is claimed.

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
Remove obsolete application hashes rather than keeping disabled or dedicated
compatibility code. Retained digests protect only named publication/distribution
artifact integrity, never local freshness. Preserve ordinary collection hashing,
non-hash data, safe paths, real artifact validity and previous outputs on failure.
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
