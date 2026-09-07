# Phase 54: Semantic Component Model Metadata for Dashboard

Status: PLANNED

Plan date: 2026-09-07

## Goal

Publish faithful, machine-readable Component model metadata that allows Textus CBD Support to render semantic DomainModel and Use Case views without parsing CML source or reconstructing semantics from names.

The target consumer is Textus CBD Support Phase 9 Component Dashboard. Cozy remains the authority for CML/model transformation; Dashboard presentation remains outside Cozy.

## Planning Rule

Each subphase is intended to fit within approximately six hours of focused work once prerequisites are available. Split a subphase before implementation if it proves materially larger.

## Phase 54.1: Existing Metadata Inventory and Stable Identity

Inventory current CML IR/generated metadata for Entity, Value, Aggregate, relations, generalization, trait, powertype, Workflow, StateMachine, and Use Case.

Freeze the stable identity and cross-reference rules needed to navigate among these elements without name-based guessing.

## Phase 54.2: Structure Metadata

Preserve Entity, Value, Aggregate, composition, aggregation, and association as distinct semantic constructs.

Where declared by CML, metadata should retain endpoint roles, cardinality, navigability, ownership, independent existence, creation/deletion policy, reassignment/reparenting policy, lifecycle propagation, and aggregate boundary.

Composition and aggregation must not collapse into a generic association.

## Phase 54.3: Classification Metadata

Preserve generalization, trait, and powertype as distinct semantics while supplying cross-reference information sufficient for one integrated Classification View.

Multiple independent powertype dimensions must remain distinguishable.

## Phase 54.4: Workflow Metadata

Preserve Workflow identity and purpose, activities, control-flow relations, branch/merge information, participants, affected domain elements, related operations/events, and declared state effects where modeled.

## Phase 54.5: StateMachine Cross-Reference Metadata

Preserve states, transitions, triggers, guards, actions, owning/affected domain element, and stable links to related Workflow activities, operations, events, and rules where declared.

## Phase 54.6: Use Case Metadata

Preserve actor, goal, trigger, preconditions, main/alternative/exception flows, postconditions, participating domain elements, operations/events, collaborators, and realizing Workflow where modeled.

## Phase 54.7: Publication Contract and Consumer Fixture

Define/version the machine-readable publication contract, add representative fixtures, and verify that a downstream consumer can traverse:

    Use Case -> Workflow -> StateMachine -> Entity

and static Structure/Classification references without CML source parsing.

Use a representative CBD Support-oriented fixture but keep the contract consumer-neutral.

## Boundaries

- Cozy owns CML syntax, semantic IR, transformation, and publication metadata.
- Cozy does not own Textus CBD Support Dashboard rendering.
- Cozy does not own CNCF runtime enforcement of lifecycle semantics.
- Missing CML semantics remain explicit absence; generators must not invent lifecycle policy.
- Existing public metadata compatibility must be reviewed before replacing or extending schemas.

## Dependencies

This Phase is a supplier phase for Textus CBD Support Phase 9, especially Phase 9.4 through Phase 9.10.

The lifecycle semantics published by this Phase may later be consumed by CNCF Phase 72. Cozy does not define the CNCF enforcement mechanism.

## Completion Conditions

Phase 54 closes when the published metadata can faithfully express the supported Structure, Classification, Workflow, StateMachine, and Use Case semantics with stable cross-view identity, representative fixtures pass, and unsupported semantics are explicit rather than synthesized.
