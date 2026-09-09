# Phase 49: Document Project Presentation Semantics Workflow Authority

Status: COMPLETE

Plan date: 2026-09-06
Revised: 2026-09-09
Closure prepared: 2026-09-09

Development item: DEV-018

Predecessors: Phase 46, Phase 46.1, and Phase 48
Successor: [Phase 49.1](phase-49.1.md)

## Goal

Make the Phase-46 presentation-semantics authority a first-class immutable
Document Project Work Product with one deterministic dependency/state model,
and freeze the public confirmation-operation grammar that the later children
implement. This retained first child owns the only remaining interacting
workflow-state, identity, dependency, and public-operation-contract decision.

Phase 49 does not redefine the semantic schema, scaffold contract, or
cross-media renderer. Read-only workflow surfaces, confirmation routing, and
operational stale acceptance belong to Phases 49.1, 49.2, and 49.3.

## Split note — 2026-09-09

The user approved `49 -> 49.1 -> 49.2 -> 49.3` through the explicit
`$cncf-split-phase Phase 49` request. The pre-split 25–31 h estimate
materially exceeded the preferred 4–8 h packing band. No P49 Step, Slice,
validation, review, or release record existed to move; every unfinished item
is partitioned exactly once below.

| Phase | Closure result | Estimate | Cost role |
| --- | --- | --- | --- |
| 49 | Workflow Work Product/state/dependency contract and public confirmation grammar. | 7–8 h | expensive reasoning kernel |
| 49.1 | Strict verify plus inspect/plan/dashboard presentation-semantics surfaces. | 6–8 h | lower-cost execution |
| 49.2 | Public confirmation operation using the accepted Phase-46.1 kernel. | 5–6 h | lower-cost execution |
| 49.3 | Stale propagation and Article-9-shaped local operational acceptance. | 7–8 h | lower-cost execution |

The expensive reasoning kernel is the interacting immutable Work Product,
state/dependency, identity/currentness, and public-operation grammar decision.
Its durable handoff is the accepted Phase-49 workflow-state/public-operation
contract. The later children consume that handoff in order rather than
reopening it. The split adds three Phase/checklist pairs, handoffs, focused
reviews, full validations, and release closures; this is accepted because it
isolates the protected decision and makes the settled execution regions
independently closable under `gpt-5.6-terra / high`. No child is below four
hours; short-child merge exceptions do not apply, and profile cost alone did
not reject any merge.

### Pre-split gate evidence

The former Phase Plan Gate reported `SPLIT_REQUIRED` for the combined 25–31 h
scope. It is dated pre-split evidence only and is not this child’s current
gate.

### Current structural gate

Phase Plan Gate: PROCEED

- target: approximate-six-hour packing target; preferred 4–8 h band
- planning_demand: protected-decision
- recommended_parent_profile: gpt-5.6-terra / xhigh
- profile_cost_role: expensive reasoning kernel
- expensive_reasoning_kernel: immutable presentation-semantics Work Product,
  dependency/state/currentness, and public confirmation-operation grammar
- frozen_profile_transition_handoff: produces the accepted Phase-49
  workflow-state/public-operation contract for Phases 49.1 through 49.3
- parent_reasoning_mode_policy: standard
- estimated_at_recommended_profile: 7–8 h; within preferred band
- merge_attempts_for_every_sub_4h_child: none
- adjacent_merge_structural_rejection_evidence: none
- profile_cost_only_rejection_forbidden: true
- short_child_exception: none
- overhead_tradeoff: three added Phase closures isolate the protected contract,
  make later state-surface/adapter/driver work independently reviewable, and
  limit xhigh use to the decision-bearing child
- agent_reasoning_mode_policy: default standard; consider pro only at an
  eligible agent launch when frozen quality-first evidence justifies it
- runtime_suitability: re-evaluate in the Phase execution task
- source: approved split from Phase 49

## P49-01: Workflow Work Product, State, and Dependency Contract

Stage Status:
- Current status: DONE
- Owner: Phase 49 P49-01/P49-02 acceptance
- Update rule: DONE because every P49-01 checklist item is checked. The Phase
  is complete only with its separate full-validation, independent-review, and
  release-closure evidence.

- Register `presentation-semantics` as a first-class `authority` Work Product in
  the immutable `document-production` workflow.
- Bind its direct dependency to `content-core` and its downstream dependencies
  to article/slide/video semantic production and cross-media confirmation.
- Keep workflow definition and provider bindings Cozy-owned; do not copy the
  DAG into `document-project.yaml`.
- Derive deterministic states sufficient to distinguish `missing`,
  `authoring-incomplete`, `invalid`, `current`, and `stale` semantic authority.
- Do not treat `authoring-incomplete` as successful Phase-46 validation.
- Do not create accepted semantic identity, coverage success, or confirmation
  receipt before strict semantic validation succeeds.

## P49-02: Public Cross-Media Confirmation Grammar

Stage Status:
- Current status: DONE
- Owner: Phase 49 P49-01/P49-02 acceptance
- Update rule: DONE because every P49-02 checklist item is checked. Parser,
  adapter, output, and receipt behavior remain owned by Phase 49.2; Phase
  closure remains open under the Phase-49 checklist.

- Freeze the public Document Project command/operation grammar for shared
  presentation confirmation; prefer an extension of the existing review
  command family.
- Reserve `presentation.render-confirmation` from generic `run` in both
  dry-run and recording forms with `DP-OP-001` before participation, provider,
  attempt, output, receipt, or state behavior; this does not admit the future
  review parser kind or change help.
- Freeze the boundary between article-specific review and the shared
  cross-media semantic confirmation Work Product.
- Freeze the deterministic default-output contract that Phase 49.2 must
  implement, including the confirmation HTML and receipt destination roles.
- Do not implement the route, renderer adapter, receipt emission, or stale
  propagation in this child.

## Moved unfinished scope

- [Phase 49.1](phase-49.1.md) owns strict verify plus inspect, plan, and
  Dashboard behavior.
- [Phase 49.2](phase-49.2.md) owns public confirmation routing and reuse of
  the accepted Phase-46.1 renderer/receipt/coverage kernel.
- [Phase 49.3](phase-49.3.md) owns stale propagation and the Article-9-shaped
  operational driver.

## Exclusions

- Scaffold generation or generated semantic skeleton changes owned by Phase 48.
- Changes to `cozy.content-core.v1`.
- Changes to `cozy.content-core.presentation-semantics.v2`.
- New or permissive semantic validators.
- A second cross-media renderer or receipt implementation.
- Automatic Story Flow / Explanation Structure authoring or acceptance.
- New PowerPoint, PDF, or video renderer capability.
- Article 8 migration or retrofit.
- Article-9-specific profile creation.
- Publication, deployment, registration, upload, push, or external mutation.

## Completion Criteria

Phase 49 completes when `presentation-semantics` is an immutable first-class
Document Project authority with deterministic dependencies and states, and the
accepted public confirmation-operation grammar/destination contract is frozen.
The matching checklist, focused executable specifications, review, full Cozy
validation, and release closure apply only to this child. Later read-only
surfaces, confirmation routing, stale propagation, and Article-9-shaped driver
acceptance remain separately planned.

## Release Closure

Phase 49 closes the immutable Document Project `presentation-semantics`
authority, its deterministic workflow state/dependency contract, and the public
cross-media confirmation-operation grammar only. The release evidence is the
sealed full Phase review
`0a7215b9ee2aab884c8a529c8732a8da1d1919d7a033435aec252df6b4f532e4`
with no Current Phase Blocker, Hygiene, or Development Candidate, plus the
required full Cozy suite (`88630-20260909T022507Z`: 1,779 succeeded, 0 failed,
133 suites completed).

The handoff to Phase 49.1 is the accepted static Work Product/state/dependency
and public-operation grammar. Read-only workflow surfaces, public confirmation
routing, stale propagation, Article-9-shaped operational acceptance,
publication, deployment, upload, push, and external mutation remain outside
this closure.

## Primary References

- `docs/phase/phase-48.md`
- `docs/phase/phase-49.1.md`
- `docs/phase/phase-49.2.md`
- `docs/phase/phase-49.3.md`
- `docs/notes/document-project-presentation-semantics-workflow-integration-specification-proposal.md`
- `docs/notes/document-project-presentation-semantics-operational-integration-specification-proposal.md`
- `docs/journal/2026/09/2026-09-06-presentation-semantics-workflow-integration-phase-49.md`
- `docs/journal/2026/09/2026-09-06-article-9-presentation-semantics-operational-integration.md`
- `docs/spec/document-project-presentation-semantics.md`
- `docs/spec/document-project.md`
- `docs/phase/phase-46.md`
- `docs/phase/phase-46.1.md`
