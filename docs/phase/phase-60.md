# Phase 60: Document Confirmation Views and Article HTML Build Targets

split_full_test_policy=final-only
validation_ownership=aggregate-deferred
aggregate_validation_owner=PHASE-60.1
aggregate_validation_sequence=["PHASE-60","PHASE-60.1"]
status=closed

Status: CLOSED
Plan recorded: 2026-09-14
Closed: 2026-09-15
Development item: DEV-028

## Phase Plan Gate

Phase Plan Gate: PROCEED

- target: calibrated expected duration centered on 6h; allowed ceiling 8h
- estimate_calibration: Phase 59 documented 7–8 h and observed 318 min in the
  same repository and Document Project validation/review/release setting;
  Phase 60 adds three target/build surfaces, so no shorter-duration multiplier
  is applied.
- planning_demand: protected-decision
- recommended_parent_profile: gpt-5.6-terra / xhigh
- profile_cost_role: time-bound partition; no reasoning-cost isolation
- expensive_reasoning_kernel: none
- frozen_profile_transition_handoff: none
- parent_reasoning_mode_policy: standard
- estimated_at_recommended_profile: 7.0–7.8 h including focused validation,
  independent review, release bookkeeping and commit; within the 8h ceiling.
- incoming_semantic_handoffs: []
- merge_attempts_for_every_sub_4h_child: none
- rebalance_attempts_for_every_sub_5h_child: none
- adjacent_merge_structural_rejection_evidence: merging this Phase with
  PHASE-60.1 restores the 860-minute Phase 60 estimate and exceeds the
  480-minute ceiling.
- profile_cost_only_rejection_forbidden: true
- short_child_basis: none
- overhead_tradeoff: one additional Phase review and release commit; the
  aggregate final-only policy avoids duplicating the repository-full SBT suite.
- agent_reasoning_mode_policy: default standard; consider pro only at an
  eligible agent launch with frozen quality-first evidence.
- runtime_suitability: re-evaluate in the Phase execution task.
- source: applied split from Phase 60 on 2026-09-14.

## Purpose and boundary

Expose structure-centered and document-centered HTML from the same Document
Description DSL, plus actual `index.dox` rendering through SmartDox, as local
Cozy Document Project targets. Manage dependencies with ordinary make-level
file modification times, without hash-based confirmation artifact management.

This first delivery unit owns only the three local views and their callable Cozy
operation contracts. It does not create a Codex authoring workflow or edit a
skill. Phase 60.1 consumes this Phase's committed local-build handoff for the
separate goal-driven DSL dependency-resolution and generation workflow.

This revises the unimplemented Phase 60 article/Core annotation proposal in
place, as requested by the user. It preserves closed Phase 58.2 and independent
Phase 59/61 work and priorities. Article 9 is an isolated driver, not
authorization to edit or publish that site.

## Planning inputs and completion ledger

- [Proposal](../notes/document-project-confirmation-views-and-make-dependencies-proposal.md)
- [Decision journal](../journal/2026/09/2026-09-14-confirmation-views-and-make-dependencies-decision.md)
- [Prose authority](../journal/2026/09/2026-09-14-document-description-prose-authority-decision.md)
- [Checklist](phase-60-checklist.md): sole completion ledger
- [Earlier Phase 60 decision](../journal/2026/09/2026-09-14-smartdox-article-and-core-confirmation-decision.md): planning history

The proposal and journal are non-normative planning inputs. P600-01 promotes
the local build boundary. The later authoring-workflow discussion is owned by
[Phase 60.1](phase-60.1.md).

## Subphase 60A: Three-view local build vertical slice

| Step | Observable outcome | Status | Closure basis |
| --- | --- | --- | --- |
| P600-01 | Frozen source/view/target/CLI contracts, reference UI and make-level dependency specifications | DONE | Checklist P600-01 |
| P600-02 | Document-centered DSL projection with secondary Core/diagram navigation and preserved structure/Summary views | DONE | Checklist P600-02 |
| P600-03 | Project-local actual-article HTML target delegates normal rendering to SmartDox | DONE | Checklist P600-03 |
| P600-04 | Timestamp-driven build/reuse and transitive dependencies with failure-preserving outputs | DONE | Checklist P600-04 |
| P600-05 | Isolated fixtures, regression validation, documentation and local-build subphase acceptance | DONE | Checklist P600-05 |

## Acceptance and exclusions

Acceptance proves the three distinct views, unchanged accepted DSL prose,
normal SmartDox article rendering, operable CLI and make-style incremental
build behavior. Renderer freshness is relative to its actual inputs, not proof
that the latest Document prose has been reflected in `index.dox`.

Core annotations in `index.dox`, new hash/receipt management, new PDF/slides/video
renderers, whole-site/Antora builds, publication, upload, deployment and edits to
external driver projects are excluded. Skill authoring/reconciliation belongs
to Phase 60.1 and its explicit execution boundary. Existing native source
validation and acceptance/export contracts are not silently migrated. Phase
59/61 remain independent capability dependencies where applicable.

The independently specified, tested and reviewed media repair in
`873694f6dca450e11d48ab70f0969e60176be115` is external release-tree context.
It is neither accepted nor retested or re-reviewed by Phase 60.

## Applied split

On 2026-09-14, the pre-goal Phase Entry Gate classified the unsplit Phase 60
as `SPLIT_REQUIRED` for the single reason `time-bound`: calibrated expected
duration 860 minutes against a 480-minute ceiling. This document retains
P600-01 through P600-05. P600-06 through P600-08 moved once, still OPEN, to
[Phase 60.1](phase-60.1.md) and its [checklist](phase-60.1-checklist.md).

The children are sequential. Phase 60.1 receives the committed local target,
CLI and validation contract from this Phase through its release handoff. The
added overhead is one independent review and release commit; the final-only
aggregate policy leaves the repository-full SBT suite with Phase 60.1. No
reasoning-cost saving or external-scope authority is claimed by this split.

## Closure and successor handoff

P600-01 through P600-05 are complete through accepted Step commits, focused
validation, accepted Step reviews, and the clean Epoch 2 independent full
Phase review. The separate release commit records this closure.

This Phase is `aggregate-deferred`: it deliberately does not run or claim the
repository-full SBT suite. Phase 60.1 remains PLANNED and is the named final
owner for that one aggregate validation after it completes P600-06 through
P600-08. No Phase 60.1 implementation starts with this closure.
