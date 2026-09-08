# Phase 54: Site-Editing-Ready Model Metadata Foundation

Status: PLANNED

Plan date: 2026-09-07
Revised: 2026-09-09
Successor: [Phase 54.1](phase-54.1.md)

## Goal

Freeze and publish the stable, source-attributed, versioned model-metadata
foundation that a SimpleModeling.org editing-oriented consumer can use before
it needs any individual Dashboard projection. The foundation gives every
published model element a stable identity and makes absence explicit, so a
consumer never has to guess identity from a displayed name or parse CML source.

Cozy remains the authority for CML/model transformation and metadata
publication. This Phase does not edit SimpleModeling.org, render a Dashboard,
or broaden the existing Phase 57 SimpleModeling.org target-binding authority.

## Split note — 2026-09-09

The user approved the ordered sequence `54 -> 54.1 -> 54.2 -> 54.3 -> 54.4`
through the explicit `$cncf-split-phase Phase 54` request, with the stated
priority that functionality useful to SimpleModeling.org editing be delivered
first. The pre-split 26–38 h estimate materially exceeded the preferred 4–8 h
packing band. No completed Step, Slice, validation, or acceptance record
existed to move; the unchecked scope is partitioned exactly once below.

| Phase | Closure result | Estimate | Cost role |
| --- | --- | --- | --- |
| 54 | Stable identity, source provenance, compatibility inventory, and a versioned consumer-neutral publication foundation. | 7–8 h | expensive reasoning kernel |
| 54.1 | Faithful static Structure metadata on the frozen identity/publication foundation. | 6–8 h | lower-cost execution |
| 54.2 | Faithful Classification metadata and independently addressable powertype dimensions. | 4–6 h | lower-cost execution |
| 54.3 | Faithful Workflow and StateMachine metadata with stable dynamic cross-references. | 6–8 h | lower-cost execution |
| 54.4 | Faithful Use Case metadata, cross-view navigation fixture, and consumer-neutral contract acceptance. | 6–8 h | lower-cost execution |

The expensive reasoning kernel is the v2 stable-identity, provenance, absence,
compatibility, and publication boundary. Its durable handoff is the versioned
metadata contract and identity/cross-reference vocabulary produced by this
retained first child. Each later child consumes that frozen handoff instead of
reopening consumer ownership or identity semantics.

The split adds four Phase documents, four checklists, four handoffs, focused
validation/review/commit boundaries, and final release closures. That overhead
is accepted because it isolates the one protected public-contract decision,
lets the remaining coherent implementation regions use the lower-cost
compatible `gpt-5.6-terra / high` profile, and makes the earliest useful
site-editing-oriented metadata foundation independently closable. No child is
below four hours; mandatory short-child merge exceptions do not apply. Profile
cost alone did not reject any merge.

### Pre-split gate evidence

The former Phase Plan Gate reported `SPLIT_REQUIRED` for the combined 26–38 h
scope. It is dated pre-split evidence only and is not the current gate of this
retained child.

### Current structural gate

Phase Plan Gate: PROCEED

- target: approximate-six-hour packing target; preferred 4–8 h band
- planning_demand: protected-decision
- recommended_parent_profile: gpt-5.6-terra / xhigh
- profile_cost_role: expensive reasoning kernel
- expensive_reasoning_kernel: versioned stable identity, provenance, explicit
  absence, compatibility, and consumer-neutral publication-contract boundary
- frozen_profile_transition_handoff: produces `cozy.cml.model-metadata.v2`
  identity and publication-contract handoff for Phases 54.1 through 54.4
- parent_reasoning_mode_policy: standard
- estimated_at_recommended_profile: 7–8 h; within preferred band
- merge_attempts_for_every_sub_4h_child: none
- adjacent_merge_structural_rejection_evidence: none
- profile_cost_only_rejection_forbidden: true
- short_child_exception: none
- overhead_tradeoff: four added Phase handoffs isolate the protected public
  contract and allow four independently reviewable lower-cost implementation
  closures; the early editing-oriented foundation and reduced reopening risk
  outweigh the added overhead
- agent_reasoning_mode_policy: default standard; consider pro only at an
  eligible agent launch when frozen quality-first evidence justifies it
- runtime_suitability: re-evaluate in the Phase execution task
- source: approved split from Phase 54

## In-scope work

| ID | Outcome | Status |
| --- | --- | --- |
| MMD-54-01 | Inventory existing CML IR and v1 generated metadata for every planned model element, including compatibility constraints. | planned |
| MMD-54-02 | Freeze stable model-element IDs, source provenance, explicit-absence, and cross-reference vocabulary without name-based guessing. | planned |
| MMD-54-03 | Define the versioned, consumer-neutral publication envelope and compatibility policy that later metadata projections extend. | planned |
| MMD-54-04 | Produce a durable handoff that lets an editing-oriented SimpleModeling.org consumer identify and cite model elements without CML parsing. | planned |

## Closure criteria

- The existing CML IR and v1 publication surfaces are inventoried with their
  compatibility constraints.
- Every supported published element has a stable, source-attributed identity;
  missing semantics are represented as explicit absence rather than invented
  policy.
- The versioned publication envelope and cross-reference vocabulary are frozen
  as the handoff for Phases 54.1 through 54.4.
- An editing-oriented consumer can address the foundation without parsing CML
  source or guessing from names, without claiming a SimpleModeling.org site
  edit, deployment, or external acceptance.
- The matching checklist, focused validation/review, and release closure for
  this child are complete. Later Structure, Classification, dynamic, and Use
  Case work remains separately planned.

## Non-goals

- Publishing Structure, Classification, Workflow, StateMachine, or Use Case
  projection detail; those each belong to a later child.
- Editing, building, registering, publishing, deploying, uploading, or pushing
  SimpleModeling.org.
- Rendering Textus CBD Support Dashboard views or redefining its presentation
  ownership.
- Defining CNCF runtime lifecycle enforcement.

## References

- [Phase 54 checklist](phase-54-checklist.md)
- [Phase 54.1](phase-54.1.md)
- [Component Dashboard model metadata note](../notes/component-dashboard-model-metadata.md)
- [SimpleModeling model integration boundary](../design/simplemodeling-model-integration-boundary.md)
