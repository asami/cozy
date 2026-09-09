# Phase 54: Semantic Component Model Metadata for Dashboard

Status: PLANNED

Plan date: 2026-09-07
Updated: 2026-09-10

## Goal

Publish faithful, machine-readable Component model metadata that allows Textus CBD Support to render semantic DomainModel, Use Case, Mono-Koto Analysis, Event Storming, Workflow/Flowchart, and related views without parsing CML source or reconstructing semantics from names.

The target consumer is Textus CBD Support Phase 9 Component Dashboard and its stakeholder/engineering projections. Cozy remains the authority for CML/model transformation and publication metadata; Dashboard and analysis-view presentation remain outside Cozy.

## Planning Rule

Each subphase is intended to fit within approximately six hours of focused work once prerequisites are available. Split a subphase before implementation if it proves materially larger.

## Phase 54.1: Existing Metadata Inventory and Stable Identity

Inventory current CML IR/generated metadata for Entity, Value, Aggregate, relations, generalization, trait, powertype, Operation/Command, Event, Workflow, StateMachine, Use Case/Actor, external Component/dependency, Query/View, and existing terminology/BoK references.

Freeze stable semantic identity and cross-reference rules needed to navigate among these elements without name-based guessing.

Classify each required cross-reference as already available, derivable faithfully from admitted semantic IR, or missing. Missing semantics remain explicit gaps.

## Phase 54.2: Structure Metadata

Preserve Entity, Value, Aggregate, composition, aggregation, and association as distinct semantic constructs.

Where declared by CML, metadata should retain endpoint roles, cardinality, navigability, ownership, independent existence, creation/deletion policy, reassignment/reparenting policy, lifecycle propagation, and aggregate boundary.

Composition and aggregation must not collapse into a generic association.

## Phase 54.3: Classification Metadata

Preserve generalization, trait, and powertype as distinct semantics while supplying cross-reference information sufficient for one integrated Classification View.

Multiple independent powertype dimensions must remain distinguishable.

## Phase 54.4: Workflow and Behavioral Cross-Reference Metadata

Preserve Workflow identity and purpose, activities, control-flow relations, branch/merge information, participants, affected domain elements, related operations/events, rules/reactions, and declared state effects where modeled.

Add stable cross-references sufficient to follow admitted behavioral causality where available:

```text
Operation / Command
  -> affected Entity / Aggregate
  -> emitted Event

Event
  -> cause / originating Operation or Event
  -> affected Entity / Aggregate
  -> Workflow Rule / Reaction
  -> downstream Operation / Command / Event
```

These links support several downstream projections, including Event Model, Workflow, StateMachine, and Event Storming. The publication contract must distinguish declared semantic relations from derived convenience indexes.

## Phase 54.5: StateMachine Cross-Reference Metadata

Preserve states, transitions, triggers, guards, actions, owning/affected domain element, and stable links to related Workflow activities, operations, events, and rules where declared.

Ensure transition triggers/effects can be correlated with admitted Event and Workflow identities without label matching.

## Phase 54.6: Use Case and Actor Metadata

Preserve actor, goal, trigger, preconditions, main/alternative/exception flows, postconditions, participating domain elements, operations/events, collaborators, and realizing Workflow where modeled.

Actor identity is the normal semantic source for stakeholder/runtime-independent actor navigation. Publish stable links from Actor/Use Case to related workflows, operations, and participating elements where the model supplies them.

Do not infer business Actors from operation names, package names, implementation callers, or runtime principals.

## Phase 54.7: Terminology / BoK Semantic Linkage

Preserve explicit links between CML semantic elements and glossary/BoK terminology where declared or admitted by an authoritative source.

The contract should support stable references for:

```text
semanticElementId
  -> termRef
     - term identity
     - vocabulary/profile identity
     - relation kind
     - source attribution
     - preferred/localized label when supplied
```

BoK Mono/Koto classification and CML model classification remain separate axes. Cozy must not collapse them into one enum.

Mono and Koto remain downstream analysis projections rather than mandatory CML element types:

```text
Mono != Entity
Koto != Event
```

Cozy does not silently resolve synonym candidates from string similarity. Terminology identity and synonym curation remain glossary/BoK or explicit human decisions.

## Phase 54.8: Event Storming Support Inventory and Cross-Model Contract

Verify that published semantics are sufficient for a downstream consumer to construct an Event Storming-style behavioral projection without source parsing or semantic guessing.

The target traversal is:

```text
Actor
  -> Command / Operation
  -> Aggregate / Entity
  -> Domain Event
  -> Workflow Rule / Policy / Reaction
  -> subsequent Command / Operation / Event
```

Where authoritative CML semantics exist, include stable links for external Component/dependency and Query/View/read-model concepts. If CML does not currently model a required concept, record an explicit gap rather than inventing syntax or metadata-only semantics.

Event Storming itself is not introduced as a new Cozy runtime/model abstraction solely for presentation purposes.

## Phase 54.9: Publication Contract and Consumer Fixtures

Define/version the machine-readable publication contract and add representative fixtures.

Verify that a downstream consumer can traverse at least:

```text
Term <-> Semantic Element
Use Case -> Actor -> Operation
Operation -> Entity/Aggregate -> Event
Event -> Workflow Rule/Reaction -> Operation/Event
Workflow -> StateMachine -> Entity
```

Also verify that:

- Mono-Koto terminology navigation can be projected from admitted links;
- Event Storming-style causal traversal can be projected from admitted links;
- Workflow metadata remains faithful enough for both engineering Workflow rendering and an intentionally simplified downstream Flowchart rendering;
- unsupported semantics remain explicit rather than synthesized.

Use representative CBD Support-oriented fixtures but keep the publication contract consumer-neutral.

## Boundaries

- Cozy owns CML syntax, semantic IR, transformation, stable semantic identity, and publication metadata.
- Cozy publishes admitted terminology/model references but does not own glossary/BoK synonym decisions.
- Cozy does not own Textus CBD Support Dashboard, Mono-Koto, Event Storming, Flowchart, or Review rendering.
- Cozy does not own CNCF runtime enforcement or runtime-evidence semantics.
- Missing CML semantics remain explicit absence; generators must not invent lifecycle, actor, causal, terminology, or grouping semantics.
- Existing public metadata compatibility must be reviewed before replacing or extending schemas.
- Workflow metadata remains faithful; downstream presentation simplification must not alter the semantic contract.

## Dependencies

This Phase is a supplier phase for Textus CBD Support Phase 9, especially the communication/analysis, static, dynamic, and cross-view stages.

The lifecycle and behavioral semantics published by this Phase may also be consumed by CNCF Phase 72 and related runtime work. Cozy does not define CNCF enforcement or runtime presentation.

Relevant planning records:

- `docs/notes/bok-glossary-cml-classification-alignment.md`
- `docs/notes/cml-analysis-view-semantic-metadata.md`
- `docs/journal/2026/09/2026-09-10-analysis-view-semantic-metadata-direction.md`

## Completion Conditions

Phase 54 closes when the published metadata can faithfully express the supported Structure, Classification, Operation/Event, Workflow, StateMachine, Use Case/Actor, and admitted terminology semantics with stable cross-view identity; downstream fixtures can perform terminology navigation and Event Storming-style traversal without CML source parsing or name-based reconstruction; Workflow remains faithful for engineering consumers; representative fixtures pass; and unsupported semantics are explicit rather than synthesized.
