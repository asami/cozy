# Phase 54: Semantic Strength and Component Model Metadata for Downstream Views

Status: PLANNED

Plan date: 2026-09-07
Updated: 2026-09-10

## Goal

Guarantee and publish the **model semantic strength** required for supported downstream semantic views. Textus CBD Support must be able to construct DomainModel, Use Case, Mono-Koto Analysis, Event Storming, Entity, Structure, Classification, Event, Workflow, Flowchart, and StateMachine projections without parsing CML source or reconstructing semantics from names.

The unit of success is a semantic capability, not a metadata field. Cozy remains authority for CML/model transformation, semantic IR, stable semantic identity, and publication contracts. Presentation remains outside Cozy.

## Semantic Strength Rule

```text
View Requirement
      -> Required Semantic Capability
      -> CML / Cozy Semantic IR
      -> Published Semantic Contract
      -> Faithful downstream projection
```

For every claimed supported view, Phase 54 must identify its required semantic capabilities and demonstrate that the publication contract supplies them. If authoritative semantics are unavailable, record an explicit capability gap. Do not create metadata-only meaning or require consumers to guess.

Working minimum capability targets:

| View | Required semantic strength |
| --- | --- |
| Mono-Koto | stable identity, terminology/BoK linkage, admitted conceptual grouping/link information, structural/behavioral cross-reference |
| Use Case | Actor, goal, trigger, flows, participants, operations/events, realizing Workflow |
| Event Storming | Actor, Command/Operation, Aggregate/Entity, Event, cause/consequence, affected subject, Workflow policy/reaction, subsequent action/event; external system and Query/View where modeled |
| Entity | identity, Entity/Value/Aggregate distinction and ownership/identity semantics |
| Structure | relation kind, roles, cardinality, navigability, composition/aggregation/association, ownership/lifecycle |
| Classification | generalization, trait, powertype, independent powertype dimensions |
| Event | Command/Event, cause/origin, consequence/reaction, affected subject and behavioral cross-reference |
| Workflow | identity/purpose, activities, control flow, branches/merges, participants, rules/reactions, operations/events, effects |
| Flowchart | faithful Workflow source strength; simplification is downstream only |
| StateMachine | subject, states, transitions, triggers, guards, actions/effects and behavioral cross-reference |

## Planning Rule

Each subphase should fit within approximately six hours of focused work once prerequisites are available. Split before implementation if materially larger.

## Phase 54.1: Semantic Capability Inventory and Stable Identity

Inventory current CML IR/generated metadata and classify each required view capability as:

- already guaranteed;
- faithfully derivable from admitted semantic IR;
- partially represented;
- missing.

Cover Entity, Value, Aggregate, relations, generalization, trait, powertype, Operation/Command, Event, Workflow, StateMachine, Use Case/Actor, external Component/dependency, Query/View, and terminology/BoK references.

Freeze stable semantic identity and cross-reference rules. Produce a capability-gap ledger before adding presentation-driven fields.

## Phase 54.2: Structure Semantic Strength

Guarantee enough structural semantics to distinguish Entity, Value, Aggregate, composition, aggregation, and association faithfully.

Preserve endpoint roles, cardinality, navigability, ownership, independent existence, create/delete policy, reassignment/reparenting, lifecycle propagation, and aggregate boundary where modeled.

Composition and aggregation must not collapse into generic association.

## Phase 54.3: Classification Semantic Strength

Guarantee generalization, trait, and powertype as distinct semantics with stable cross-references. Preserve multiple independent powertype dimensions.

## Phase 54.4: Behavioral and Workflow Semantic Strength

Guarantee Workflow identity/purpose, activities, control flow, branches/merges, participants, affected elements, operations/events, rules/reactions, and state effects where modeled.

Support admitted behavioral causality:

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

Distinguish declared relations from faithfully derived convenience indexes.

## Phase 54.5: StateMachine Semantic Strength

Guarantee states, transitions, triggers, guards, actions, owning/affected subject, and stable links to Workflow activities, operations, events, and rules where declared.

Transition trigger/effect correlation must not depend on label matching.

## Phase 54.6: Use Case and Actor Semantic Strength

Guarantee Actor, goal, trigger, preconditions, main/alternative/exception flows, postconditions, participating elements, operations/events, collaborators, and realizing Workflow where modeled.

Actor identity is the normal source for stakeholder actor navigation. Do not infer business Actors from implementation/runtime naming.

## Phase 54.7: Terminology / BoK Semantic Strength

Preserve explicit links between semantic elements and glossary/BoK terminology where declared/admitted.

```text
semanticElementId
  -> termRef
     - term identity
     - vocabulary/profile identity
     - relation kind
     - source attribution
     - preferred/localized label when supplied
```

BoK Mono/Koto classification and CML classification remain separate. `Mono != Entity` and `Koto != Event`. Cozy does not silently resolve synonym candidates.

If Mono-Koto projection requires conceptual grouping not represented authoritatively, record that capability as missing rather than infer it.

## Phase 54.8: Event Storming Semantic Strength

Verify that admitted semantics support downstream traversal:

```text
Actor
  -> Command / Operation
  -> Aggregate / Entity
  -> Domain Event
  -> Workflow Rule / Policy / Reaction
  -> subsequent Command / Operation / Event
```

Where authoritative semantics exist, include external Component/dependency and Query/View/read-model links. Missing concepts remain explicit capability gaps.

Event Storming itself is not a new Cozy model abstraction solely for presentation.

## Phase 54.9: Semantic Strength Publication Contract and Consumer Fixtures

Define/version the machine-readable contract and validate capabilities rather than field presence.

Fixtures must demonstrate, where modeled:

```text
Term <-> Semantic Element
Use Case -> Actor -> Operation
Operation -> Entity/Aggregate -> Event
Event -> Workflow Rule/Reaction -> Operation/Event
Workflow -> StateMachine -> Entity
```

They should also answer representative semantic questions:

- Which Actor initiates this intent/operation?
- Which subject does this Command affect?
- Which Event results and what caused it?
- Which Workflow reaction follows?
- Which terminology concept is associated with this element?
- What lifecycle semantics distinguish this structural relation?
- Which StateMachine transition is triggered?

When the source lacks sufficient semantic strength, fixtures must prove explicit absence rather than guessed completion.

Verify Mono-Koto terminology navigation, Event Storming causal traversal, and faithful Workflow semantics sufficient for both engineering rendering and intentionally simplified downstream Flowchart rendering.

## Boundaries

- Cozy owns model semantic strength: CML syntax, semantic IR, transformation, stable identity, semantic capability, and faithful publication.
- Glossary/BoK owns curated terminology and synonym decisions; Cozy publishes admitted references.
- CNCF owns runtime semantic strength: execution/enforcement and attributable runtime evidence.
- Textus CBD Support owns projection/review and reports insufficient supplier semantic strength as gaps.
- Cozy does not own Dashboard, Mono-Koto, Event Storming, Flowchart, or Review rendering.
- Missing semantics remain explicit absence; generators must not invent lifecycle, actor, causal, terminology, or grouping semantics.
- Workflow remains faithful; presentation simplification is downstream.

## Dependencies

This Phase supplies Textus CBD Support Phase 9 communication/analysis, static, dynamic, and cross-view projections. Its lifecycle/behavioral semantics may also be consumed by CNCF Phase 72 and related runtime work.

Relevant records:

- `docs/notes/bok-glossary-cml-classification-alignment.md`
- `docs/notes/cml-analysis-view-semantic-metadata.md`
- `docs/journal/2026/09/2026-09-10-analysis-view-semantic-metadata-direction.md`

## Completion Conditions

Phase 54 closes only when:

1. required semantic capabilities for every claimed supported view are documented;
2. each capability is classified as guaranteed, faithfully derivable, or explicitly unavailable;
3. the publication contract exposes stable identities and sufficient semantic strength without name-based reconstruction;
4. consumer fixtures prove semantic traversals/questions rather than only schema-field presence;
5. Mono-Koto terminology navigation and Event Storming-style traversal are faithful where supported;
6. Structure, Classification, Event, Workflow, StateMachine, and Use Case semantics meet their documented capability requirements;
7. Workflow remains faithful while downstream Flowchart simplification requires no weakening of the source contract; and
8. unsupported semantic strength is represented as explicit absence/gaps rather than synthesized meaning.
