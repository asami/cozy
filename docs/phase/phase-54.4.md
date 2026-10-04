# Phase 54.4: Use Case and Actor Metadata Handoff

Status: IN_PROGRESS
Current status: S54.4.1 / S54.4.1A, S54.4.2 / S54.4.2A and S54.4.3 / S54.4.3A CLOSED. Phase closure remains open.
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
| MMD-544-02 | Preserve stable Use Case-to-Workflow and onward dynamic/static references without name-based guessing. | complete; S54.4.2 navigation and S54.4.3 fixed-resource continuation proof accepted |
| MMD-544-03 | Preserve explicit absence when a source does not declare an Actor, flow, collaborator, or realizing Workflow. | complete; S54.4.1 accepted |
| MMD-544-04 | Freeze the Use Case/Actor handoff for Terminology, Event Storming, and final CBD Support fixtures. | complete; S54.4.3 supplier handoff accepted; final Phase closure pending |

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
| S54.4.2 / S54.4.2A | Addressable actor/use-case/core lookup and Local/External reference resolution without guessing. | CLOSED; immutable navigation and UN-01–07 accepted; 172 tests in twelve suites, both 100-case properties with zero discards; independent Step review PASS; parent manual local acceptance commit. |
| S54.4.3 / S54.4.3A | Two fixed UTF-8 resources, cross-view consumer fixture proof and durable supplier handoff. | CLOSED; two complete resources and UF-01–09 accepted; 181 tests in thirteen suites, three 100-case properties with zero discards; independent Step review PASS; parent manual local acceptance commit. |

S54.4.1 is CLOSED on 2026-10-04. The declared-semantics and explicit-absence
obligations are accepted. Navigation is accepted separately below; fixed
handoff is accepted below and final closure remains unchecked. Initial focused validation passed 165 tests in eleven suites. The
independent Step review found that UC-09 did not actually reorder Catalog
records. The repaired suite passed all 17 tests and 100 generated cases with
zero discards, exercising declared and anonymous-gap modes in every case.
Independent focused re-review closed that sole finding with no new findings or
evidence gaps. Both runs ended with SBT and wrapper exit zero and the serial
lock released. This Step's acceptance commit binds the ten supplier paths;
the native Git history records its revision.

S54.4.2 is CLOSED on 2026-10-04. Focused validation passed 172 tests in twelve
suites. Navigation and retained Use Case metadata properties each passed 100
generated cases with zero discards. SBT and wrapper exited zero and the serial
lock was released. Independent Step review passed the complete navigation
accumulator with no blockers, Hygiene or Development Candidates. This Step's
manual local acceptance commit binds six paths; native Git history records its
revision.

Foundation, Structure, Classification, Dynamic and legacy core contracts are
unchanged. S54.4.2 navigation and its
[executable specification](../../src/test/scala/cozy/modeler/CmlUseCaseNavigationSpec.scala)
are accepted by focused validation and independent Step review.
S54.4.3 is CLOSED on 2026-10-04. Its fixed-resource handoff,
[supplier handoff](../design/cml-use-case-metadata-handoff.md),
[UF-01–09 specification](../../src/test/scala/cozy/modeler/CmlUseCaseMetadataFixtureSpec.scala)
and [durable journal](../journal/2026/10/2026-10-04-phase-54.4-use-case-metadata.md)
record 49 complete core records/four Terms, independent same-envelope peer
consumption and declared/explicit-absence facts. Focused validation passed
181 tests in thirteen suites. Fixture, navigation and Use Case metadata
properties each passed 100 generated cases with zero discards; both resource
modes are exercised in every fixture sample. SBT and wrapper exited zero and
the serial lock was released. Independent complete Step review passed with no
blockers, Hygiene or Development Candidates. The parent manual local
acceptance commit binds ten paths; native Git history records its revision.
Phases 54.5–54.7 are
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
- [Use Case supplier handoff](../design/cml-use-case-metadata-handoff.md)
- [Dynamic supplier handoff](../design/cml-dynamic-metadata-handoff.md)
- [Foundation supplier contract](../spec/cml-semantic-foundation.md)
