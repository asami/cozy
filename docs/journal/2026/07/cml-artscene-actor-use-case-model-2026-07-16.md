# ArtScene Actor and UseCase Model

- date: 2026-07-16
- status: implementation record

ArtScene Phase 10 exposed a gap in the CML handoff. Component UseCase text was
present in generated metadata, but top-level `# ACTOR` sections were parsed as
generic documents and therefore did not participate in the model graph.

The implementation direction is:

- make top-level Actor definitions typed Kaleidox model elements;
- preserve them through Cozy and SimpleModeler as component metadata;
- preserve UseCase ID, trigger, priority, status, and flow kind;
- normalize Actor role fields into local or external references;
- publish the typed Actor and UseCase contract in the CML model sidecars;
- retain existing source compatibility;
- migrate ArtScene to canonical `MAIN FLOW` and explicit contract metadata.

The Modegramming Style UseCase series is the design source. Relationship
execution and typed step directives remain a later implementation slice rather
than being approximated in this change.
