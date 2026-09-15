# Phase 61: Video Contract Admission and Speech Normalization

status=closed
split_full_test_policy=final-only
validation_ownership=aggregate-deferred
aggregate_validation_owner=PHASE-61.1
aggregate_validation_sequence=["PHASE-61","PHASE-61.1"]

Status: CLOSED
Plan recorded: 2026-09-14
Development item: DEV-029
Split applied: 2026-09-15
Closed: 2026-09-15
Successor: [Phase 61.1](phase-61.1.md)

## Phase Plan Gate

Phase Plan Gate: PROCEED

- target: calibrated expected duration centered on 6h; allowed ceiling 8h
- estimate_calibration: no comparable completed Phase 61 implementation; the
  original 7h estimate is partitioned around the accepted authority boundary.
- planning_demand: protected-decision
- recommended_parent_profile: gpt-5.6-terra / xhigh
- profile_cost_role: expensive reasoning kernel
- expensive_reasoning_kernel: P610-01 freezes authoring fields, compatibility,
  requested/effective timing and evidence semantics before code changes.
- frozen_profile_transition_handoff: committed P610-01 contract and P610-02
  speech-policy boundary for Phase 61.1.
- parent_reasoning_mode_policy: standard
- estimated_at_recommended_profile: 4.0-5.0h; below the 6h target only because
  the authority handoff must close before the downstream timing Phase.
- incoming_semantic_handoffs: []
- merge_attempts_for_every_sub_4h_child: none
- rebalance_attempts_for_every_sub_5h_child: retain P610-02 here; moving it to
  Phase 61.1 makes this authority child sub-four-hour.
- adjacent_merge_structural_rejection_evidence: merging would erase the
  independently closed authority handoff required for lower-cost downstream
  timing execution.
- profile_cost_only_rejection_forbidden: true
- short_child_basis: unavoidable balanced 4-5h remainder after the authority
  boundary
- overhead_tradeoff: one additional review, release ledger and commit confine
  the protected contract to this Phase and permit a Terra-high successor plan.
- agent_reasoning_mode_policy: default standard; consider pro only at an
  eligible agent launch when the active interface supports it and frozen
  quality-first evidence justifies it.
- runtime_suitability: re-evaluate in the Phase execution task
- source: applied split from Phase 61 on 2026-09-15

## Purpose and boundary

Freeze the speech/display and authored post-utterance timing contract, then
provide opt-in speech-only removal of Japanese middle dots. Keep displayed
terminology unchanged. [Phase 61.1](phase-61.1.md) consumes this authority to
implement the actual-audio trailing interval and retain the current scene until
that interval finishes.

This is an independent video-authoring boundary over the existing Phase 30
workflow and Phase 31 encoding/timing infrastructure. It preserves Phase 59's
priority, separate Phase 60 planning and closed prior behavior.

## Planning inputs and completion ledger

- [Proposal](../notes/video-speech-normalization-and-tail-silence-proposal.md)
- [Decision journal](../journal/2026/09/2026-09-14-video-middle-dot-and-tail-silence-decision.md)
- [Checklist](phase-61-checklist.md): sole completion ledger

The proposal and journal are not normative implementation specifications.
P610-01 promotes the admitted policies before code changes.

## Subphase 61A: Contract admission and speech normalization

| Step | Observable outcome | Status | Closure basis |
| --- | --- | --- | --- |
| P610-01 | Frozen speech/display, nested-scene and timing contracts with executable specifications | CLOSED | Focused review disposition `12b16f32735797b2737049ad0a3a0270c9246a48fc3326d6fbd5cc5bc311e4e8`; Checklist P610-01 |
| P610-02 | Opt-in speech-only middle-dot policy, including dictionary-generated readings | CLOSED | Commit `44b9e2f`; focused Step review `69737b8327b8355b8e86ce3dfd2683fc94168418d97272e31f388cbd82b94b8f`; Checklist P610-02 |

## Acceptance and exclusions

Acceptance demonstrates unchanged display strings and corrected provider
reading, while P610-01 fixes the authored timing policy that the successor
implements. Phase 61.1 owns a requested final post-utterance pause,
audio/picture/evidence timing and independently preserved summary/credits holds.

Global punctuation rewriting, caption typography redesign, PDF/slides,
content authoring, external-provider changes, external-project edits, site
registration/build, upload, publication, deployment and push are excluded.
Planning does not rebuild the current SimpleModeling.org video.

## Split handoff

P610-01 and P610-02 are CLOSED and accepted in commits `7675b25` and
`44b9e2f`. The Phase full review identified and the focused re-review resolved
the single documentation-boundary blocker `CB-P61-001`; no blockers remain.
The closed contract and implemented speech-policy boundary now hand off to
[Phase 61.1](phase-61.1.md) for authored trailing-silence, pipeline and
isolated-acceptance work. Phase 61.1 remains successor-only and unstarted.
The final-only aggregate policy defers this Phase's repository-full SBT suite
to Phase 61.1; the focused Step receipts, reviews, release evidence and
commit recorded here do not claim that aggregate suite.

## Split provenance

The user requested this split on 2026-09-15. The applied sequence is
`PHASE-61 -> PHASE-61.1`, with `reasoning-cost-isolation` and
`semantic-boundary` reasons. Phase 61.1 receives the frozen P610-01/P610-02
authority handoff exactly once; the detailed split decision is retained in
ignored workflow state. No completed Step, product source, external project,
publication or deployment work moved in this planning operation.
