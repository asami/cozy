# Phase 71.4: Video Ending and Complete Media-Regeneration Acceptance

status=planned
split_full_test_policy=final-only
split_full_validation_method=sbt-full-suite
split_validation_bootstrap=none
validation_ownership=aggregate-final-owner
aggregate_validation_owner=PHASE-71.4
aggregate_validation_sequence=["PHASE-71","PHASE-71.1","PHASE-71.2","PHASE-71.3","PHASE-71.4"]
depends_on=phase-71.3.md
execution_priority=current_video_generation_blocker

Status: planned; implementation not started
Planned at: 2026-09-29
Development item: DEV-034
Primary owner: Cozy
Split from [Phase 71](phase-71.md).
Predecessor: [Phase 71.3](phase-71.3.md).
Former numeric successor: [Phase 72](phase-72.md), unchanged and not started.

## Goal

Diagnose and fix the reported black video ending without losing narration, then prove the complete original media-regeneration contract on the accepted serial ancestry. Own the sequence's one repository-full SBT validation and final cross-product acceptance.

## Scope and owned Steps

- P710-F04: reproduce Model Harness's reported black ending. Compare actual final decoded frames, renderer frame count, muxed audio/video end timestamps and player EOF before selecting a correction.
- Keep an opaque end card through the common media end without clipping narration or appending black frames. Verify Japanese VOICEVOX and English macOS-say, fractional-frame duration and AAC padding, direct render and final assembly.
- P710-04-INTEGRATION: rerun the already accepted real Core/intermediate/Storyboard/reuse and article-only/video-only complete site workflows on the combined serial tree; this is integration verification, not reassignment or reopening of accepted predecessor Steps.
- Audit all application subsystems against Phase 71's removal/retention inventory: no non-integrity helper, model, codec, field, scaffold or hash-dependent test remains. Verify actual retained integrity mismatch detection is confined to its publication/distribution boundary.
- Verify historical non-hash data, `parts[].script`, current assets/effective renderer/narration settings, actionable genuine failures and preservation of previous successful outputs.
- Verify committed predecessor closure/handoffs and accepted ancestry, then run the sequence's one repository-full SBT suite on the final release tree.

## Phase Plan Gate

Phase Plan Gate: PROCEED

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

Complete original media/video/site acceptance evidence, removal audit, final-frame/timestamp evidence and aggregate full-validation release binding. No successor starts automatically.

No genuine incoming semantic authority/human/external-operation/release/migration
edge is asserted: `incoming_semantic_handoffs=[]`. These are serial delivery
and contract dependencies, not fabricated semantic gates. Close only this unit's owned checklist outcomes; integration rechecks do not reopen predecessors.
Routine execution included in a Sol/high container is limited to implementing
and proving that container's discovered contract or root-cause correction;
settled document/media and site/PDF work is assigned to Terra/high successors.

## Validation and closure boundary

[Phase 71.4 Checklist](phase-71.4-checklist.md) is the completion ledger.
Every owned Step/Slice needs executable focused validation and appropriate
compile/consumer coverage, independent acceptance review, exact Step commits,
closure journals/ledger, mandatory full Phase review and a distinct release
commit. No item is implemented or accepted by this planning split.

This Phase accepts aggregate responsibility for PHASE-71, PHASE-71.1,
PHASE-71.2 and PHASE-71.3 plus its own accepted tree. Verify every predecessor's
committed deferred-validation handoff and serial ancestry before the one
configured repository-full SBT validation and final release. Earlier deferred
records stay historical; this does not retroactively rewrite their assurance.

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
