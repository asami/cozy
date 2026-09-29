# Phase 71.3: Local Site and PDF Currentness with Purpose-Bound Integrity

status=planned
split_full_test_policy=final-only
split_full_validation_method=sbt-full-suite
split_validation_bootstrap=none
validation_ownership=aggregate-deferred
aggregate_validation_owner=PHASE-71.4
aggregate_validation_sequence=["PHASE-71","PHASE-71.1","PHASE-71.2","PHASE-71.3","PHASE-71.4"]
depends_on=phase-71.2.md
execution_priority=current_video_generation_blocker

Status: planned; implementation not started
Planned at: 2026-09-29
Development item: DEV-034
Primary owner: Cozy
Split from [Phase 71](phase-71.md).
Predecessor: [Phase 71.2](phase-71.2.md).
Successor: [Phase 71.4](phase-71.4.md).

## Goal

Complete the local media/site-registration and PDF currentness correction with the accepted operation/schema/video contracts. Preserve only explicitly justified publication/distribution artifact-integrity digests; local generation, reuse and prebuilt adoption do not consume them as freshness or approval gates.

## Scope and owned Steps

- P710-03-SITE: reconcile site binding, registry, build context, PDF receipt/review-state and prebuilt registration adapters with declared producer/actual direct inputs, existence, modification times and explicit adoption. Retire the temporary site-video/PDF SHA exceptions.
- P710-04-SITE: use isolated copies of the current SimpleModeling.org package and its same complete local site-build entry. Replace only prebuilt video, then only article; both reach the final generated site without manual receipt edits/hash transfers or regenerating an unaffected video.
- Reproduce `application-modeling.dox/media-publication.json`: an `index.dox`-only update that leaves `article/article-en.pdf` and its actual producer inputs unchanged must not fail `publication-article-en` receipt or package-wide PDF review-state currentness.
- Missing/invalid PDFs and truly newer declared PDF inputs still select genuine rejection/regeneration/diagnostics. Surface stage, product, missing/invalid producer/input and next operation, not bare stale or schema-only errors.
- Retain only inventoried publication/distribution artifact-integrity digests with a named purpose and actual mismatch proof. Publication-related class placement alone is not a retention reason. Delete non-integrity calculations and supporting contracts; do not merely bypass checks.
- Correct additional same-operation blockers exposed by the real entry inside the admitted Cozy boundary; no replacement receipt/approval/hash exception.

## Phase Plan Gate

Phase Plan Gate: PROCEED

- target: expected duration centered on 6h; allowed ceiling 8h
- estimate_calibration: no directly comparable measured evidence; scope-based planning, low confidence; no model-speed multiplier
- planning_demand: bounded-settled
- recommended_parent_profile: gpt-5.6-terra / high
- profile_cost_role: lower-cost execution
- expensive_reasoning_kernel: none: accepted operation/schema/video contracts determine implementation
- frozen_profile_transition_handoff: accepted Phase 71.2 contract and handoff described below
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

Successful complete isolated site-entry evidence, selected-PDF-input currentness, named retained digest-purpose ledger with genuine integrity rejection, and actionable/atomic failure cases. Phase 71.4 consumes this accepted site contract.

No genuine incoming semantic authority/human/external-operation/release/migration
edge is asserted: `incoming_semantic_handoffs=[]`. These are serial delivery
and contract dependencies, not fabricated semantic gates. Close only this unit's owned checklist outcomes; integration rechecks do not reopen predecessors.
Routine execution included in a Sol/high container is limited to implementing
and proving that container's discovered contract or root-cause correction;
settled document/media and site/PDF work is assigned to Terra/high successors.

## Validation and closure boundary

[Phase 71.3 Checklist](phase-71.3-checklist.md) is the completion ledger.
Every owned Step/Slice needs executable focused validation and appropriate
compile/consumer coverage, independent acceptance review, exact Step commits,
closure journals/ledger, mandatory full Phase review and a distinct release
commit. No item is implemented or accepted by this planning split.

Repository-full SBT validation alone is deferred to [Phase 71.4](phase-71.4.md)
under the reciprocal final-only chain. At release record focused assurance and
`repository_full_suite=deferred-not-run`; do not claim ordinary full-suite
assurance.

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
