# Phase 71.1: Document and Media Hash Removal

status=closed
closure_disposition=normal-closed-user-defined-completion-criteria
prior_closure_attempts=withdrawn-including-exception-closure
closure_record=2026-10-01-phase-71.1-normal-closure.md
latest_candidate_validation=warning-free-clean-compile-and-storyboard-spec-passed
latest_focused_validation=user-manual-storyboard-spec-16-passed-warnings-0-20261001T203025JST
structural_audit=187-owner-relocations-repaired; clean-main-test-compile-passed-warnings-0
latest_compile_validation=user-manual-clean-Test-compile-success-warnings-0-20261001T202810JST
release_commit=not-created
local_commit=authorized-by-later-user-request
split_full_test_policy=final-only
split_full_validation_method=sbt-full-suite
split_validation_bootstrap=none
validation_ownership=aggregate-deferred
aggregate_validation_owner=PHASE-71.4
aggregate_validation_sequence=["PHASE-71","PHASE-71.1","PHASE-71.2","PHASE-71.3","PHASE-71.4"]
depends_on=phase-71.md
execution_priority=current_video_generation_blocker

Status: CLOSED; current user-defined completion criteria satisfied
Planned at: 2026-09-29
Development item: DEV-034
Primary owner: Cozy
Split from [Phase 71](phase-71.md).
Predecessor: [Phase 71](phase-71.md).
Successor: [Phase 71.2](phase-71.2.md).

## Current completion boundary (2026-10-01)

The user explicitly instructed normal closure after warning-free compilation
and successful Storyboard review. The current completion criteria are now
those verified outcomes and a consistent evidence/ledger handoff. Clean
compilation at 20:28:10 JST compiled 257 main and 175 test sources with zero
warnings/errors. `CozyVideoStoryboardReviewSpec` passed all 16 tests at
20:30:25 JST with zero warnings/errors. The [normal closure record](../journal/2026/10/2026-10-01-phase-71.1-normal-closure.md)
records the user instruction, evidence and consolidation of original checklist
criteria. All original IDs are retained, with prior wording preserved there.

The Phase is CLOSED. Earlier premature closure attempts and the exception
closure remain withdrawn; no DP relocation decision supplies completion.
Full repository `test`, runtime classpath export, broader Phase review and
Step/release commits were not performed on this latest tree and are outside
the revised closure criteria. Their execution or success is not asserted.
The existing PHASE-71.4 aggregate full-suite plan remains for its own scope.
No successor starts automatically.

All existing uncommitted changes remain preserved. Shared-skill repair,
rejected helper rewiring, push and successor execution stay discontinued.
An ordinary local commit is now authorized by the later user request.
The acceptance proof is the fresh clean compile and focused executable
specification, supported by the [structural repair journal](../journal/2026/10/2026-10-01-outer-reference-repair.md).

## Goal

Consume Phase 71's frozen operation/digest-purpose inventory and remove non-integrity application hash computation, models, fields and codecs in Document Project, media and the inventoried adjacent non-video/non-publication consumers. Make declared-input timestamps and explicit adoption authoritative while preserving useful non-hash metadata and real validity failures.

## Implementation scope and original Steps

These implementation goals remain historical context. Their original closure
obligations are consolidated into the current user-defined acceptance boundary
in the checklist and normal closure record; broader validation is not implied.

- P710-03-DM: replace receipt/projection/internal identity hashes with the already-decided operation/dependency/currentness behavior. Remove computation, comparisons, dedicated fields, models, codecs, configuration and hash-dependent tests rather than leaving disabled diagnostics or compatibility logic.
- Include the inventory's adjacent application consumers and immediate cross-module callers necessary for a compile-valid, contract-coherent deletion. Video-specific and publication/site outcomes remain with their named successors; no unassigned non-integrity application use may escape the inventory.
- P710-04-DM: prove missing/newer inputs, all-current reuse, ambiguous timestamps selecting conservative regeneration, timestamp-preserved explicit replacement, useful historical non-hash data and previous-output preservation.
- Historical extra hash properties may be ignored by ordinary loading or removed by migration; do not keep dedicated hash parsing/calculation/validation/re-emission. Preserve required non-hash inputs and source semantics.

## Phase Plan Gate

Phase Plan Gate: PROCEED

- target: expected duration centered on 6h; allowed ceiling 8h
- estimate_calibration: no directly comparable measured evidence; scope-based planning, low confidence; no model-speed multiplier
- planning_demand: bounded-settled
- recommended_parent_profile: gpt-5.6-terra / high
- profile_cost_role: lower-cost execution
- expensive_reasoning_kernel: none: operation/schema/removal direction is frozen by predecessor
- frozen_profile_transition_handoff: accepted Phase 71 contract and handoff described below
- parent_reasoning_mode_policy: standard
- estimated_at_recommended_profile: expected 7h; range 6–10h; 1h above target, fits expected <=8h ceiling; upper range is risk evidence only
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

Accepted document/media schemas and APIs, removed-field/consumer migration matrix and executable timestamp/adoption/historical-loading/failure contract. Phase 71.2's video and Phase 71.3's site adapters consume these accepted interfaces.

No genuine incoming semantic authority/human/external-operation/release/migration
edge is asserted: `incoming_semantic_handoffs=[]`. These are serial delivery
and contract dependencies, not fabricated semantic gates. Close only this unit's owned checklist outcomes; integration rechecks do not reopen predecessors.
Routine execution included in a Sol/high container is limited to implementing
and proving that container's discovered contract or root-cause correction;
settled document/media and site/PDF work is assigned to Terra/high successors.

## Validation and closure boundary

[Phase 71.1 Checklist](phase-71.1-checklist.md) is the completion ledger.
All ten retained entries satisfy the current criteria explicitly selected by
the user: warning-free clean main/test compile, successful Storyboard review
executable specification, recorded focused source review and consistent
validation/handoff records. No commit or push was required for closure. The user subsequently authorized
an ordinary local commit; see the [local commit record](../journal/2026/10/2026-10-01-phase-71.1-local-commit.md). Push remains unauthorized.
The normal closure record preserves original checklist wording and the
criterion changes; unperformed broad validation/review/commit work is not
presented as complete.

`repository_full_suite=deferred-not-run`; its existing aggregate plan remains
with [Phase 71.4](phase-71.4.md). Runtime classpath export remains unrun.
These are not blockers under the current user-defined Phase closure criteria.
The repository already has `build.sbt`; `split_validation_bootstrap=none`.
No successor execution accompanies closure.

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
