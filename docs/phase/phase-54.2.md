# Phase 54.2: Faithful Classification Metadata

Status: IN_PROGRESS (S54.2.1 authored; validation and acceptance pending)

Plan date: 2026-09-09
Split from: [Phase 54](phase-54.md)
Depends on: Phase 54.1
Successor: [Phase 54.3](phase-54.3.md)

## Purpose

Publish Classification metadata that keeps generalization, trait, and
powertype distinct while giving an editing-oriented or Dashboard consumer the
stable cross-references needed for one integrated Classification View. Multiple
independent powertype dimensions must remain independently addressable.

## Provenance and structural gate

This is the third sequential child of the 2026-09-09 approved Phase 54 split.
It consumes Phase 54's frozen cozy.cml.semantic-metadata.v1
foundation/handoff and the Phase
54.1 static-model vocabulary. It produces the Classification vocabulary and
fixtures consumed by the later dynamic and cross-view acceptance children.

Phase Plan Gate: PROCEED

- target: approximate-six-hour packing target; preferred 4–8 h band
- planning_demand: bounded-settled
- recommended_parent_profile: gpt-5.6-terra / high
- profile_cost_role: lower-cost execution
- expensive_reasoning_kernel: none
- frozen_profile_transition_handoff: Phase 54 cozy.cml.semantic-metadata.v1
  foundation/handoff and Phase 54.1 Structure vocabulary
- parent_reasoning_mode_policy: standard
- estimated_at_recommended_profile: 4–6 h; within preferred band
- merge_attempts_for_every_sub_4h_child: none
- adjacent_merge_structural_rejection_evidence: none
- profile_cost_only_rejection_forbidden: true
- short_child_exception: none
- overhead_tradeoff: a separate Classification closure prevents its distinct
  topology and powertype-dimension semantics from being obscured by Structure
  or dynamic-model review
- agent_reasoning_mode_policy: default standard; consider pro only at an
  eligible agent launch when frozen quality-first evidence justifies it
- runtime_suitability: re-evaluate in the Phase execution task
- source: approved split from Phase 54

## In-scope work

| ID | Outcome | Status |
| --- | --- | --- |
| MMD-542-01 | Publish generalization, trait, and powertype as distinct metadata constructs with stable identities. | S54.2.1 authored; acceptance pending |
| MMD-542-02 | Publish cross-references sufficient to project one integrated Classification topology without name-based reconstruction. | S54.2.1 references authored; S54.2.2 topology pending |
| MMD-542-03 | Preserve multiple independent powertype dimensions without conflation. | S54.2.1 authored; S54.2.2 queries pending |
| MMD-542-04 | Freeze Classification fixtures and the later-child handoff. | planned |

## Execution Steps

Execution date: 2026-10-03. Original split, dependencies and historical profile
recommendation above are retained. The executing parent uses the user-selected
gpt-6.1-sol/high profile and the frozen PHASE-54.2 plan epoch.

| Stage | Current status | Owner | Update rule | Closure basis |
| --- | --- | --- | --- | --- |
| S54.2.1 / CLASSIFICATION | 81 focused tests passed; independent review and M0 authoring closure accepted; manual commit pending | Cozy Classification supplier; parent owns acceptance | Preserve semantic-metadata.v1 and Structure; change only the complete frozen Classification schema | Focused Classification plus predecessor accumulator, independent focused review, parent manual Step commit |
| S54.2.2 / TOPOLOGY | Pending | Cozy Classification topology supplier | Consume admitted declarations and exact qualified IDs; preserve anonymous, external and absent facts | Integrated node/edge/dimension query specifications, focused validation/review, parent manual Step commit |
| S54.2.3 / FIXTURE-HANDOFF | Pending | Cozy receiver-fixture and handoff supplier | Supply deterministic declared/absence envelopes and actionable Phase54.3/54.4 handoff without consumer wiring claims | JSON-only resource specifications, focused validation/review, parent manual Step commit |
| Phase acceptance and release | Pending | Parent | Exactly one comprehensive Phase review per attributable PLAN epoch; final ordinary full Cozy test and separate manual release commit | Three accepted Steps, sole comprehensive review, final full validation, canonical checklist/index/strategy/journal closure and native completion audit |

The [Classification specification](../spec/cml-classification-metadata.md),
[design](../design/cml-classification-metadata.md) and
[executable CL-01–09](../../src/test/scala/cozy/modeler/CmlClassificationMetadataSpec.scala)
are accepted S54.2.1 supplier evidence: 81 focused tests passed and the
independent review finding was closed by an exact M0 authoring amendment. The
manual Step commit remains pending. S54.2.1 completes the internal typed producer and
strict JSON child schema; CML/CLI producers, external consumers, topology and
receiver resources are not claimed as delivered by this Step. Topology and
fixture/handoff source authoring belong to their pending Steps.

## Closure criteria

- Generalization, trait, and powertype have distinct, stable, source-attributed
  representations.
- Classification navigation uses frozen stable IDs and retains independently
  declared powertype dimensions.
- Fixtures demonstrate the integrated topology without CML parsing.
- The Classification handoff is frozen for Phases 54.3 and 54.4; no dashboard
  rendering, external site edit, or runtime enforcement is claimed.

## Non-goals

- Reopening Phase 54 identity/publication or Phase 54.1 Structure semantics.
- Workflow, StateMachine, Use Case, or final cross-view traversal acceptance.
- SimpleModeling.org site mutation, publication, deployment, upload, or push.

## References

- [Phase 54](phase-54.md)
- [CML Semantic Foundation Handoff](../design/cml-semantic-foundation-handoff.md)
- [Phase 54.1](phase-54.1.md)
- [Phase 54.2 checklist](phase-54.2-checklist.md)
- [Phase 54.3](phase-54.3.md)
