# CML Semantic Metadata and Semantic Strength for Model Views

Date: 2026-09-10

## Purpose

This note records the Cozy-side requirements needed for downstream tools such as Textus CBD Support to project Mono-Koto Analysis, Use Case, Event Storming, Entity, Structure, Classification, Event, Workflow, Flowchart, and StateMachine views from one canonical semantic model.

Cozy does not own those presentation views. Cozy owns CML syntax, semantic IR, transformation, and faithful machine-readable publication metadata.

## Semantic Strength Principle

Cozy's responsibility is stronger than publishing a collection of metadata fields. Cozy must provide and publish enough **semantic strength** for each supported downstream view to be constructed faithfully, without source parsing, name-based guessing, or consumer-side reconstruction of missing meaning.

```text
View Requirement
      |
      v
Required Semantic Capability
      |
      v
CML / Cozy Semantic IR
      |
      v
Published Semantic Contract
      |
      v
Faithful downstream projection
```

A semantic capability is an explicitly modeled or faithfully derivable meaning such as stable identity, relation kind, lifecycle ownership, Actor participation, event causality, Workflow control flow, or StateMachine transition semantics.

A view is supported only to the extent that the published semantic contract satisfies its required capabilities.

If:

```text
required semantic capability > available admitted semantic capability
```

Cozy must publish explicit absence/gap information. The downstream consumer must not be expected to infer the missing semantics.

## View Capability Matrix

The following matrix is the working minimum semantic-strength target. Exact schema fields are subordinate to these capabilities.

| View | Minimum semantic capabilities |
| --- | --- |
| Mono-Koto | stable semantic identity, terminology/BoK linkage, admitted conceptual grouping/link information, structural and behavioral cross-references |
| Use Case | Actor, goal, trigger, flow semantics, participating elements, related operations/events, realizing Workflow where modeled |
| Event Storming | Actor, Command/Operation, Aggregate/Entity, Domain Event, causal links, affected subject, Workflow policy/reaction, subsequent action/event; external system and Query/View where modeled |
| Entity | stable identity, Entity/Value/Aggregate distinction, ownership/identity semantics |
| Structure | relation kind, endpoints/roles, cardinality, navigability, composition/aggregation/association distinction, ownership/lifecycle semantics |
| Classification | generalization, trait, powertype, independent powertype dimensions and stable references |
| Event | Command/Event distinction, origin/cause, consequence/reaction, affected subject, related Workflow/StateMachine semantics |
| Workflow | identity/purpose, activities, control flow, branches/merges, participants, related operations/events, rules/reactions, effects |
| Flowchart | no additional weaker source model; a consumer may project a simplified subset from faithful Workflow semantics |
| StateMachine | owning/affected subject, states, transitions, triggers, guards, actions/effects and cross-references to events/workflows |

This matrix defines semantic requirements, not a mandate for one metadata object per view.

## Connected Semantic Graph

Cozy should publish a sufficiently connected semantic graph:

```text
CML
 |
 v
Cozy semantic IR
 |
 +-- stable semantic identity
 +-- terminology / BoK references
 +-- Entity / Value / Aggregate
 +-- Operation / Command
 +-- Event
 +-- Workflow / Rule / Reaction
 +-- StateMachine
 +-- Use Case / Actor
 +-- External Component / dependency
 +-- Query / View
 |
 v
machine-readable semantic metadata
```

Missing semantics remain explicit absence. Cozy must not invent semantic links merely to make a downstream diagram complete.

## Terminology / BoK Linkage

BoK Mono/Koto classification and CML model classification are different axes and must not be collapsed into one enum.

Cozy should preserve explicit links from CML semantic elements to terminology/BoK entries where declared or admitted by an authoritative source.

```text
semanticElementId
  -> termRef
     - termId
     - vocabulary / profile identity
     - preferred label if supplied
     - language if supplied
     - relation kind
     - source attribution
```

Cozy does not decide that similar strings are synonyms. Synonym resolution and terminology inconsistency review remain glossary/BoK or explicit human work.

## Mono-Koto Support

Mono and Koto are downstream analysis projections, not mandatory Cozy runtime/model classes.

```text
Mono candidate
  <-> terminology
  <-> Entity / Value / Aggregate / Structure semantics

Koto candidate
  <-> terminology
  <-> Use Case / Operation / Command / Event / Workflow / State effects
```

A one-to-one mapping is explicitly not required: `Mono != Entity` and `Koto != Event`.

If explicit conceptual grouping/analysis annotations are not present, Cozy must report that capability as absent rather than manufacture grouping from naming or layout.

## Event Storming Support

Event Storming requires a stronger behavioral semantic capability than an Event list. Cozy should support traversal, where modeled:

```text
Actor
  -> Command / Operation
  -> affected Aggregate / Entity
  -> Domain Event
  -> Workflow Rule / Policy / Reaction
  -> subsequent Command / Operation / Event
```

Relevant cross-references include Use Case/Actor, Command/Operation, affected Entity/Aggregate, emitted Event, event cause/origin, consequence/reaction, Workflow activity/rule, external interaction, Query/View, and StateMachine trigger/effect.

The publication contract must distinguish declared relationships from faithfully derived convenience indexes and preserve authority/source attribution.

## Actor Semantics

Actor identity should normally come from Use Case semantics. Cozy preserves stable Actor identity and admitted links to Use Cases, workflows, operations, and participating elements.

Do not infer business Actors from operation names, runtime principals, package names, or caller implementation classes.

## Workflow and Flowchart

Cozy publishes faithful Workflow semantics. Flowchart does not justify a weaker semantic model: a downstream consumer may intentionally simplify Workflow presentation while the source contract remains faithful.

## Semantic Strength Validation

Phase and publication validation should test capabilities rather than merely field presence.

A fixture should demonstrate that a consumer can answer semantic questions such as:

- Which Actor initiates this Use Case/Command?
- Which Aggregate/Entity is affected by this Command?
- Which Event results, and what caused it?
- Which Workflow reaction follows this Event?
- Which terminology concept is associated with this model element?
- Is this relation composition, aggregation, or association and what lifecycle meaning does it carry?
- Which transition is triggered by this Event?

If the authoritative model does not contain enough information to answer a question, the fixture should verify explicit absence rather than a guessed answer.

## Responsibility Boundary

- Glossary/BoK owns curated domain terminology and synonym decisions.
- Cozy owns CML syntax, semantic IR, transformation, semantic capability, stable identity, and faithful publication of admitted semantics.
- CNCF owns runtime semantic strength: execution/enforcement and attributable runtime evidence.
- Textus CBD Support owns projection/review: presenting available semantics through stakeholder and engineering views and reporting insufficient semantic strength as gaps.

The architectural split is therefore:

```text
Cozy        -> model semantic strength
CNCF        -> runtime semantic strength
CBD Support -> projection / review
```

The goal is not to encode downstream views in Cozy. The goal is to guarantee that every claimed supported view can be a faithful projection of the semantic model at the semantic strength actually available.
