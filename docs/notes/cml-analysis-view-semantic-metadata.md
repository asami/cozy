# CML Semantic Metadata for Analysis Views

Date: 2026-09-10

## Purpose

This note records the Cozy-side requirements needed for downstream tools such as Textus CBD Support to project Mono-Koto Analysis, Use Case, Event Storming, Event, Workflow, Flowchart, and StateMachine views from one canonical semantic model.

Cozy does not own those downstream presentation views. Cozy owns CML syntax, semantic IR, transformation, and faithful machine-readable publication metadata.

## Core Principle

Cozy should publish a sufficiently connected semantic graph so downstream tools can project multiple views without parsing CML source or reconstructing semantics from names.

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
 |
 v
CBD Support projections
```

Missing semantics must remain explicit absence. Cozy must not invent semantic links merely to make a downstream diagram complete.

## Terminology / BoK Linkage

Existing BoK glossary and CML classification alignment remains valid: Mono/Koto classification and CML model classification are different axes and must not be collapsed into one enum.

For downstream analysis views, Cozy should additionally preserve explicit links from CML semantic elements to terminology/BoK entries where declared or otherwise admitted by an authoritative source.

Representative published linkage:

```text
semanticElementId
  -> termRef
     - termId
     - vocabulary / profile identity
     - preferred label if supplied by source
     - language if supplied
     - relation kind
```

Possible relation kinds may include `represents`, `uses-term`, `realizes`, or another small typed set, but the exact vocabulary should be frozen only after existing IR and BoK contracts are inventoried.

Cozy does not decide that similar strings are synonyms. Synonym resolution, terminology inconsistency review, and human curation belong to the glossary/BoK and consumer workflow.

## Mono-Koto Support

Mono and Koto are downstream analysis projections, not new required runtime/model classes in Cozy.

Cozy should publish enough semantic identity and grouping/link information for a consumer to associate:

```text
Mono candidate
  <-> terminology
  <-> Entity / Value / Aggregate / Structure semantics

Koto candidate
  <-> terminology
  <-> Use Case / Operation / Command / Event / Workflow / State effects
```

A one-to-one mapping is explicitly not required:

```text
Mono != Entity
Koto != Event
```

If CML later gains explicit conceptual grouping or analysis annotations, they may be published as semantic grouping metadata. Until then, Cozy should not heuristically invent such grouping from naming or diagram layout.

## Event Storming Support

Event Storming is a downstream cross-model projection. Cozy should make the following traversal possible where the semantics exist in CML/IR:

```text
Actor
  -> Command / Operation
  -> affected Aggregate / Entity
  -> Domain Event
  -> Workflow Rule / Policy / Reaction
  -> subsequent Command / Operation / Event
```

Required cross-references should include, where modeled:

- Use Case -> Actor
- Use Case -> Operation / Command
- Operation / Command -> affected Entity / Aggregate
- Operation / Command -> emitted Event
- Event -> cause / originating Operation or Event
- Event -> affected Entity / Aggregate
- Event -> consequence / downstream reaction
- Workflow activity -> Operation / Command / Event
- Workflow rule/reaction -> triggering Event and resulting activity/operation/event
- Workflow -> participating Actor / Use Case / domain element
- External interaction -> external Component / dependency
- Query/View -> relevant domain elements/events when explicitly modeled
- StateMachine transition -> trigger Event/Operation and affected subject

The publication contract should distinguish declared semantic relationships from derived convenience indexes. A consumer must be able to determine the authority/source of a link.

## Actor Semantics

Actor identity should normally come from Use Case semantics. Cozy should preserve stable Actor identity and links to Use Cases, workflows, operations, and participating elements where modeled.

Do not infer business Actors from operation names, runtime principals, package names, or caller implementation classes.

## Workflow and Flowchart

Cozy must publish faithful Workflow semantics. A downstream Flowchart view may simplify those semantics for stakeholder communication, but Cozy must not weaken Workflow metadata for that purpose.

Workflow publication should preserve purpose, activities, control flow, branches/merges, participants, affected domain elements, operations/events, rules/reactions, and declared state effects.

## External System and Query/View

Event Storming commonly needs external systems and read models. Cozy should inventory whether CML already has authoritative representations for:

- external Component / subsystem / dependency interaction;
- Query / View / read-model semantics.

If present, Phase 54 should publish stable cross-references. If absent, record explicit gaps rather than introducing speculative syntax inside the metadata layer.

## Publication Contract Requirements

The semantic metadata contract should support downstream traversal without source parsing, including at minimum:

```text
Term <-> Semantic Element
Use Case -> Actor -> Operation
Operation -> Entity/Aggregate -> Event
Event -> Workflow reaction -> Operation/Event
Workflow -> StateMachine -> Entity
```

All cross-references should use stable semantic identities rather than labels.

## Responsibility Boundary

- Glossary/BoK owns curated domain terminology and synonym decisions.
- Cozy owns CML syntax, semantic IR, transformation, and publication of admitted terminology/model links.
- CNCF owns runtime semantics and attributable runtime evidence.
- Textus CBD Support owns Mono-Koto, Event Storming, Flowchart, Dashboard, Review, and cross-view presentation.

The goal is not to encode downstream views in Cozy, but to make those views faithful projections of one connected semantic model.
