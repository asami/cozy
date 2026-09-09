# Phase 56: Document Project Native Production Execution

Status: COMPLETE

Plan date: 2026-09-07

Revised: 2026-09-10

Implementation started: 2026-09-10

Closure prepared: 2026-09-10

Development item: DEV-019
Successor: [Phase 56.1](phase-56.1.md)

## Goal

Turn the existing Document Project workflow kernel into the first native
production-execution boundary. `cozy document-project run` must resolve one
admitted logical operation through one typed provider binding, validate its
pre-execution boundary, execute that provider, and return its typed result.
Acceptance of outputs, receipt, attempts, and currentness belongs to
[Phase 56.1](phase-56.1.md).

The implementation is greenfield. It does not preserve or wrap the internal
operation sequence, receipt layout, artifact placement, or provider adapter of
`cozy-article-media` or the current publication-preparation workflow.

## Split note — 2026-09-10

The user approved the ordered sequence `56 -> 56.1 -> 56.2` through the
explicit `$cncf-split-phase Phase 56` request. The pre-split 19–25 h estimate
materially exceeded the preferred 4–8 h packing band. No completed P56 Step,
Slice, validation, acceptance, or commit record existed to move. The
previously unchecked scope is partitioned exactly once below.

| Phase | Closure result | Estimate | Cost role |
| --- | --- | --- | --- |
| 56 | Typed native `run` provider dispatch and result vocabulary. | 6–8 h | lower-cost execution |
| 56.1 | Validated, atomic output/receipt/attempt/currentness closure. | 6–7 h | lower-cost execution |
| 56.2 | Closed executable state plus structural-by-default/explicit-visual verification. | 7–8 h | lower-cost execution |

The 2026-09-07 greenfield decision already fixes the public ownership,
ordering, and non-compatibility invariants. No genuinely expensive reasoning
kernel remains open: each child is bounded implementation and acceptance work.
All three children use the least-cost compatible `gpt-5.6-terra / high` profile
under the standard parent-mode policy, so this is not a model-cost-isolation
split and has no profile transition. Phase 56 produces the frozen native
`run`/provider-binding/result contract consumed by Phase 56.1. Phase 56.1
produces the frozen accepted-evidence and derived-currentness contract consumed
by Phase 56.2. The qualitative expected saving is less repeated context,
reopened cross-boundary review, and implementation rework; it is not a claimed
model-price saving.

Combining Phases 56 and 56.1 would be 12–15 h; combining Phases 56.1 and 56.2
would be 13–15 h. Each exceeds the 8 h ceiling, so neither adjacent merge is
independently closable in the preferred band. No child is below four hours;
short-child exceptions do not apply. Profile cost alone rejected no merge.

The split adds two Phase documents, two checklists, two dependency handoffs,
and their focused validation/review/commit and release-closure boundaries. That
overhead is accepted because it replaces one over-band delivery with three
independently reviewable native-production outcomes without reopening the
settled greenfield decision.

### Pre-split gate evidence

The former Phase Plan Gate reported `SPLIT_REQUIRED` for the combined 19–25 h
scope. It is dated pre-split evidence only and is not the current gate of this
retained first child.

### Current structural gate

Phase Plan Gate: PROCEED

- target: approximate-six-hour packing target; preferred 4–8 h band
- planning_demand: bounded-settled
- recommended_parent_profile: gpt-5.6-terra / high
- profile_cost_role: lower-cost execution
- expensive_reasoning_kernel: none
- frozen_profile_transition_handoff: produces the native `run` command,
  provider-binding resolution, pre-execution admission, typed provider-result,
  and missing-provider blocking contract for Phase 56.1
- parent_reasoning_mode_policy: standard
- estimated_at_recommended_profile: 6–8 h; within preferred band
- merge_attempts_for_every_sub_4h_child: none
- adjacent_merge_structural_rejection_evidence: Phase 56 + Phase 56.1 is
  12–15 h, above the <=8 h ceiling; Phase 56.1 + Phase 56.2 is 13–15 h, above
  the same ceiling
- profile_cost_only_rejection_forbidden: true
- short_child_exception: none
- overhead_tradeoff: two added handoffs turn one 19–25 h closure into three
  independently reviewable native-production boundaries; their assurance value
  outweighs the added documentation and release overhead
- agent_reasoning_mode_policy: default standard; consider pro only at an
  eligible agent launch when frozen quality-first evidence justifies it
- runtime_suitability: re-evaluate in the Phase execution task
- source: approved split from Phase 56

## P56-01: Typed Provider Execution Contract

Stage Status: COMPLETE

- Resolve exactly one logical operation and one typed provider binding.
- Validate exact input authorities, prerequisite Work Products, provider
  availability, and bounded declared output destinations before execution.
- Replace deferred/record-only execution with a typed provider result carrying
  output identities, media types, diagnostics, and receipt evidence.
- Keep a missing provider explicitly blocked; an attempt with empty outputs and
  no receipt is not successful execution or accepted evidence.

## Dependencies

- completed Document Project v2 kernel and evidence/currentness work in Phases
  45 through 45.2;
- completed logical-presentation contracts in Phases 46 and 46.1; and
- completed Phase 49 sequence through Phase 49.3 presentation-semantics workflow
  integration.

## Exclusions

- Publication Export or a SimpleModeling.org target binding, owned by Phase 57.
- Validating a returned result, appending accepted evidence, or deriving
  currentness; those belong to Phase 56.1.
- Closed executable planning state and verification-policy projection; those
  belong to Phase 56.2.
- Compatibility adapters for `cozy-article-media` or the current publication
  preparation workflow.
- Manual evidence adoption or retrospective fabricated attempts.
- Default PDF/slide/video raster review.
- Provider-specific semantic authority outside typed bindings.
- Implicit publication, deployment, upload, push, or commit.

## Completion Criteria

Phase 56 completes when a public Document Project `run` command resolves one
typed provider binding, validates pre-execution admission, executes it, and
returns a typed result or an explicit blocking/failure result without treating
an empty deferred attempt as success. Its frozen native dispatch/result contract
is sufficient for Phase 56.1 to perform evidence acceptance. Focused
specifications, review, full Cozy validation, and release closure apply to this
child only; Phases 56.1 and 56.2 remain separately planned and unstarted.

## Closure Evidence

- P56-01 was accepted in `817129f663d2ba961c92fb93933699cae8a7b018`
  (`feat(document-project): execute native review provider`).
- The full Phase review identified CPB-001 and CPB-002; the bounded cycle-1
  repair passed the 104-test representative and 121-test accumulator suites,
  and its focused re-review sealed both blockers.
- The release-header focused re-review found no Current Boundary Blocker,
  Hygiene, or Development Candidate after the required version-history update.
- The final full Cozy suite passed on the release tree: 1,802 succeeded, 0
  failed, 133 suites completed, and the SBT lock was released.
- Phase 56.1, Phase 56.2, and Phase 57 remain separately planned and
  unstarted; their documents and shared planning projections are preserved.

## References

- `docs/phase/phase-56-checklist.md`
- `docs/phase/phase-56.1.md`
- `docs/journal/2026/09/2026-09-07-document-project-production-workflow-greenfield-decision.md`
- `docs/phase/phase-49.3.md`
- `docs/spec/document-project.md`
