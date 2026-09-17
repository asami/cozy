# Analysis View Semantic Metadata and Semantic Strength Direction

Date: 2026-09-10

## Decision

Cozy will support Textus CBD Support analysis and engineering views by extending its semantic publication contract rather than introducing independent Mono-Koto, Event Storming, or presentation-specific models.

The stronger architectural decision is that **Cozy guarantees the model semantic strength required to construct each claimed supported view faithfully**.

Cozy's responsibility is not merely to publish fields. It must expose the semantic capabilities and stable cross-references needed by a consumer to construct a view without parsing CML source, matching labels, or guessing missing meaning.

```text
View Requirement
    -> Required Semantic Capability
    -> Cozy Semantic IR
    -> Published Semantic Contract
    -> Faithful Projection
```

When the available admitted semantics are weaker than a view requirement, Cozy reports an explicit capability gap. CBD Support must not compensate by reconstructing semantics heuristically.

## Working View Capability Model

Representative requirements are:

- Mono-Koto: terminology/BoK linkage plus admitted conceptual grouping and structural/behavioral cross-reference.
- Use Case: Actor, goal, trigger, flows, participants, operations/events, realizing Workflow.
- Event Storming: Actor, Command/Operation, Aggregate/Entity, Event, cause/consequence, affected subject, Workflow reaction/policy, subsequent action/event.
- Entity: stable identity and Entity/Value/Aggregate semantics.
- Structure: relation kind, cardinality, navigability, ownership and lifecycle strength sufficient to distinguish composition, aggregation, and association.
- Classification: generalization, trait, powertype and independent dimensions.
- Event: Command/Event, origin/cause, consequence/reaction and affected subject.
- Workflow: purpose, activities, control flow, participants, rules/reactions and effects.
- StateMachine: states, transitions, triggers, guards, actions/effects and owning subject.
- Flowchart: no weaker Cozy model; CBD Support may simplify faithful Workflow semantics for communication.

This is a capability contract rather than a one-schema-per-view design.

## Terminology Direction

Mono-Koto must be linked to glossary/BoK terminology. BoK Mono/Koto classification remains separate from CML model classification.

Cozy preserves explicit term references from semantic elements so consumers can navigate terminology and engineering semantics without labels. Cozy does not resolve synonyms from string similarity.

## Event Storming Direction

Event Storming is a consumer projection spanning several semantics:

```text
Use Case Actor
  -> Command / Operation
  -> Aggregate / Entity
  -> Domain Event
  -> Workflow Rule / Reaction
  -> Command / Operation / Event
```

Cozy publishes the admitted cross-references needed for this traversal. Event Storming itself does not become a CML runtime abstraction solely for presentation.

## Workflow / Flowchart Direction

Workflow metadata remains faithful. A downstream Flowchart may intentionally simplify Workflow for non-engineering communication, but no simplification leaks into Cozy's semantic model or publication contract.

## Three-System Responsibility

The responsibility split is now stated as:

```text
Cozy
  Model Semantic Strength
  "What meaning does the model guarantee?"

CNCF
  Runtime Semantic Strength
  "What modeled meaning can be executed, enforced, and observed?"

Textus CBD Support
  Projection / Review
  "How is that meaning presented, navigated, reviewed, and reported?"
```

This gives downstream consumers a clear rule: a view may only claim semantics that its supplier contract actually guarantees.

## Phase 54 Consequence

Phase 54 must be evaluated by semantic capability, not field count. Consumer fixtures should prove semantic questions and traversals, including explicit absence when required strength is unavailable.

Future views should follow the same process:

```text
new View
  -> identify required semantic capabilities
  -> compare with Cozy semantic strength
  -> extend CML/IR/publication only where authoritative semantics are genuinely missing
  -> expose explicit gaps otherwise
```

This avoids accumulating presentation-specific metadata and keeps Cozy a consumer-neutral semantic contract provider.
