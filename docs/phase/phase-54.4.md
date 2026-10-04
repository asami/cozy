# Phase 54.4: Use Case and Actor Metadata Handoff

Status: IN_PROGRESS
Current status: S54.4.1 / S54.4.1A CLOSED; S54.4.2 navigation is next. Phase closure remains open.
Owner: Cozy Use Case metadata supplier; Phase parent for validation, review, local commits and closure.
Update rule: project actual Step evidence mechanically after acceptance/commit; preserve earlier core contracts and later Phase ownership.

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
| MMD-544-01 | Publish Use Case Actor, goal, trigger, flows, conditions, postconditions, domain elements, operations/events, collaborators, and realizing Workflow where modeled. | complete; S54.4.1 accepted |
| MMD-544-02 | Preserve stable Use Case-to-Workflow and onward dynamic/static references without name-based guessing. | planned |
| MMD-544-03 | Preserve explicit absence when a source does not declare an Actor, flow, collaborator, or realizing Workflow. | complete; S54.4.1 accepted |
| MMD-544-04 | Freeze the Use Case/Actor handoff for Terminology, Event Storming, and final CBD Support fixtures. | planned |

## Closure criteria

- CBD Support can traverse each declared Use Case relationship through stable,
  source-attributed references without reparsing CML.
- Actor identity comes from modeled semantics; business Actors are never
  inferred from implementation or runtime names.
- The child leaves final contract/fixture acceptance to Phase 54.7 and makes
  no Dashboard rendering or external-consumer acceptance claim.

## Step and Slice evidence ledger

| Step / Slice | Frozen goal | Status / evidence |
| --- | --- | --- |
| S54.4.1 / S54.4.1A | Complete Actor/Use Case typed declarations, validation, strict wire read/render, full binding and explicit gaps. | CLOSED; five supplier modules and UC-01–09 accepted with 17 tests, 100 permutation cases and zero discards; initial eleven-suite validation and independent Step review followed by focused repair closure PASS; parent manual local acceptance commit. |
| S54.4.2 / S54.4.2A | Addressable actor/use-case/core lookup and Local/External reference resolution without guessing. | planned; unstarted. |
| S54.4.3 / S54.4.3A | Two fixed UTF-8 resources, cross-view consumer fixture proof and durable supplier handoff. | planned; unstarted. |

S54.4.1 is CLOSED on 2026-10-04. The declared-semantics and explicit-absence
obligations are accepted; navigation, fixed handoff and final closure remain
unchecked. Initial focused validation passed 165 tests in eleven suites. The
independent Step review found that UC-09 did not actually reorder Catalog
records. The repaired suite passed all 17 tests and 100 generated cases with
zero discards, exercising declared and anonymous-gap modes in every case.
Independent focused re-review closed that sole finding with no new findings or
evidence gaps. Both runs ended with SBT and wrapper exit zero and the serial
lock released. This Step's acceptance commit binds the ten supplier paths;
the native Git history records its revision.

Foundation, Structure, Classification, Dynamic and legacy core contracts are
unchanged. S54.4.2 is next and remains unstarted. Phases 54.5–54.7 are
unstarted. Full Phase review, ordinary full validation and distinct local
release remain pending.

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
- [Use Case metadata specification](../spec/cml-use-case-metadata.md)
- [Use Case metadata design](../design/cml-use-case-metadata.md)
- [Dynamic supplier handoff](../design/cml-dynamic-metadata-handoff.md)
- [Foundation supplier contract](../spec/cml-semantic-foundation.md)
