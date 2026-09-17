# Phase 54.4: Use Case and Actor Metadata Handoff

Status: PLANNED

Plan date: 2026-09-09
Reconciled: 2026-09-17
Split from: [Phase 54](phase-54.md)
Depends on: Phase 54.3
Successor: [Phase 54.5](phase-54.5.md)

## Purpose

Publish faithful Use Case and Actor metadata for Textus CBD Support. A consumer
can navigate Actor, goal, trigger, flows, preconditions, postconditions,
participating elements, operations/events, collaborators, and a realizing
Workflow wherever those semantics are modeled.

This child freezes the Use Case handoff. Terminology/BoK, Event Storming, and
the final cross-view fixture contract remain separately planned in Phases 54.5
through 54.7.

## In-scope work

| ID | Outcome | Status |
| --- | --- | --- |
| MMD-544-01 | Publish Use Case Actor, goal, trigger, flows, conditions, postconditions, domain elements, operations/events, collaborators, and realizing Workflow where modeled. | planned |
| MMD-544-02 | Preserve stable Use Case-to-Workflow and onward dynamic/static references without name-based guessing. | planned |
| MMD-544-03 | Preserve explicit absence when a source does not declare an Actor, flow, collaborator, or realizing Workflow. | planned |
| MMD-544-04 | Freeze the Use Case/Actor handoff for Terminology, Event Storming, and final CBD Support fixtures. | planned |

## Closure criteria

- CBD Support can traverse each declared Use Case relationship through stable,
  source-attributed references without reparsing CML.
- Actor identity comes from modeled semantics; business Actors are never
  inferred from implementation or runtime names.
- The child leaves final contract/fixture acceptance to Phase 54.7 and makes
  no Dashboard rendering or external-consumer acceptance claim.

## Non-goals

- Reopening prior identity, Structure, Classification, Workflow, or
  StateMachine contracts.
- Terminology/BoK, Event Storming, or final publication-fixture acceptance.
- CBD Support UI implementation, SimpleModeling.org mutation, or CNCF runtime
  enforcement.

## References

- [Phase 54.3](phase-54.3.md)
- [Phase 54.4 checklist](phase-54.4-checklist.md)
- [Phase 54.5](phase-54.5.md)
