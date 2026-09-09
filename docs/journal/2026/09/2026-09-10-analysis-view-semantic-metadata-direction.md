# Analysis View Semantic Metadata Direction

Date: 2026-09-10

## Decision

Cozy will support the new Textus CBD Support analysis-view direction by extending its semantic publication contract rather than by introducing independent Mono-Koto or Event Storming models.

CBD Support now treats Mono-Koto Analysis, Use Case, and Event Storming as stakeholder-facing projections over the same Canonical Component Design Model used by Entity, Event, Structure, Classification, Workflow, Flowchart, and StateMachine views.

Cozy's responsibility is therefore to publish faithful, machine-readable, cross-linked semantics with stable identities.

## Terminology Direction

Mono-Koto must be linked to glossary/BoK terminology. Cozy already distinguishes BoK mono/koto classification from CML model classification; that separation remains correct.

The additional requirement is to preserve explicit term references from CML semantic elements so consumers can navigate between terminology and engineering semantics without relying on labels.

Cozy does not resolve synonym candidates such as alternate business words based only on string similarity. Such interpretation remains attributable glossary/BoK or consumer-review work.

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

Cozy should publish cross-references that support this traversal where modeled. Event Storming itself does not become a CML runtime abstraction solely for presentation purposes.

Actor information should remain rooted in Use Case semantics. External Component/dependency and Query/View links should be published where CML already models them; missing concepts should remain explicit gaps.

## Workflow / Flowchart Direction

Workflow metadata remains faithful and complete enough for engineering consumption. The downstream Flowchart view may intentionally simplify Workflow for non-engineering communication. No simplification should leak into Cozy's semantic representation.

## Phase 54 Consequence

Phase 54 will be expanded to cover:

- terminology/BoK semantic references;
- command/event cause, consequence, and affected-domain cross-references;
- Actor links rooted in Use Case;
- workflow rule/reaction links needed for event-causal traversal;
- external-system and Query/View inventory/publication where authoritative representations exist;
- consumer fixtures proving Mono-Koto terminology navigation and Event Storming-style traversal without CML source parsing.

This keeps the architecture clean:

```text
CML -> Cozy semantic graph -> CBD Support projections
                         \
                          -> CNCF runtime semantics/evidence
```

Cozy supplies semantic truth and identity; consumers decide how to present it.
