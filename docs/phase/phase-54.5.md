# Phase 54.5: Terminology and BoK Semantic References

Status: PLANNED

Plan date: 2026-09-17
Split from: [Phase 54](phase-54.md)
Depends on: Phase 54.4
Successor: [Phase 54.6](phase-54.6.md)
Primary downstream consumer: Textus CBD Support

## Purpose

Publish admitted references between Cozy semantic elements and glossary/BoK
terminology so Textus CBD Support can navigate terminology without treating
display names or synonyms as identity.

Glossary/BoK remains the authority for curated terms and synonym decisions.
Cozy publishes only declared or otherwise admitted references and records
missing conceptual links explicitly.

## In-scope work

| ID | Outcome | Status |
| --- | --- | --- |
| MMD-545-01 | Publish stable semantic-element to terminology references with vocabulary/profile identity, relation kind, source attribution, and supplied localized/preferred labels. | planned |
| MMD-545-02 | Keep BoK Mono/Koto classification distinct from CML Entity/Event classification. | planned |
| MMD-545-03 | Record unsupported conceptual grouping or terminology links as explicit capability gaps. | planned |
| MMD-545-04 | Freeze Terminology/BoK fixtures and handoff for Event Storming and final consumer fixtures. | planned |

## Closure criteria

- CBD Support can cite an admitted term reference from a stable semantic ID.
- `Mono != Entity` and `Koto != Event`; no synonym candidate is silently
  resolved by Cozy.
- Missing terminology semantics remain explicit absence.

## Non-goals

- Curating BoK vocabulary or synonym policy.
- Inventing conceptual groups for a view.
- Event Storming traversal or final consumer fixture acceptance.

## References

- [Phase 54.4](phase-54.4.md)
- [Phase 54.5 checklist](phase-54.5-checklist.md)
- [Phase 54.6](phase-54.6.md)
