# Phase 54.6: Event Storming Causal Traversal

Status: CLOSED

Plan date: 2026-09-17
Split from: [Phase 54](phase-54.md)
Depends on: Phase 54.5
Successor: [Phase 54.7](phase-54.7.md)
Primary downstream consumer: Textus CBD Support

## Purpose

Prove that admitted Cozy semantics can supply Textus CBD Support with faithful
Event Storming-style causal traversal without introducing Event Storming as a
second Cozy source model.

```text
Actor --declared operations--> Command / Operation
Operation --admitted Cause--> Domain Event
  | affected subjects: Aggregate / Entity
Event --declared Rule / Reaction context--> subsequent Operation / Event
```

Every edge is published only when modeled or faithfully derivable from
admitted semantic IR. Missing causes, effects, reactions, external systems,
or read-model links remain explicit gaps.

## In-scope work

| ID | Outcome | Status |
| --- | --- | --- |
| MMD-546-01 | Publish admitted Actor, Command/Operation, Aggregate/Entity, Event, cause/consequence, and affected-subject links. | CLOSED |
| MMD-546-02 | Publish declared Workflow policy/reaction and subsequent action/event links where modeled. | CLOSED |
| MMD-546-03 | Include external Component/dependency and Query/View/read-model links only where admitted. | CLOSED |
| MMD-546-04 | Freeze causal fixtures and handoff for final CBD Support publication/fixture acceptance. | CLOSED |

## Stage: Derived causal supplier and traversal

Stage Status:
- Current status: CLOSED
- Owner: Cozy / Phase 54.6 parent
- Update rule: Update from actual Step validation, review and commit receipts; closure is based on the [Phase checklist](phase-54.6-checklist.md).

| Step | Outcome | Status |
| --- | --- | --- |
| [S54.6.1](s54.6.1.md) | Derived supplier, strict v1 publication and paired executable specification. | CLOSED |
| [S54.6.2](s54.6.2.md) | Qualified direct causal/context traversal over admitted Graph. | CLOSED |
| [S54.6.3](s54.6.3.md) | Fixed declared/absence JSON resources, peer compatibility and final handoff. | CLOSED |

- [x] S54.6.1 supplier accepted by focused validation, independent review and local Step commit.
- [x] S54.6.2 traversal accepted by its selected evidence and local Step commit.
- [x] S54.6.3 fixtures and final supplier handoff accepted by selected evidence and local Step commit.
- [x] All Phase obligations, sole full review, ordinary full tests and distinct release/native audit complete.

S54.6.1 is CLOSED with successful focused compilation, 227 tests in 17 suites,
CM-10 ScalaCheck 100 successes/zero discards/pass, independent S1 review PASS
and verified native local commit `1d928b39c8b83c533c55dce389cb6c6589640249`.
The parent retains `P546-S5461-REVIEW-001` revision 1 and
`s5461-parent-manual-commit-receipt-001.json` (verified) as S1 acceptance evidence.
S54.6.2 traversal and CT-01..12 are accepted after parent mechanical preflight
and selected validation: all 239 tests in 18 suites passed, including all 12 CT
behavior blocks. CT-12, CM-10, TM-09, TN-08 and TF-09 each recorded 100 successes,
zero discards and passed status; baseline category counts were positive. Fresh
independent protected-focused S2 review is PASS with no CPB, HYG or DEV items.
The parent verified manual native S2 commit
`661e11b848102faf143bf5f4dc173e5ab7bcafdd` and its actual receipt, whose parent
is the accepted S1 commit. The [S2 checklist](s54.6.2-checklist.md) is CLOSED.
S54.6.3 fixed declared/absence resources, CF-01..08 and
[supplier handoff](../design/cml-causal-metadata-handoff.md) are accepted after
parent mechanical preflight and selected validation: all 247 tests in 19 suites
passed. CF-08, CT-12, CM-10, TM-09, TN-08 and TF-09 each recorded 100 successes,
zero discards and passed status; baseline category counts were positive. CF-08
exercised both actual modes per case: declared and absence counts were each 100,
with all seven observed categories positive. SBT and wrapper exited 0 and the
serialization lock was released. Fresh independent lightweight S3 review is
PASS with no CPB, HYG or DEV items. The parent verified manual native local S3
Step commit `2360d78ac1ee24e4b9e51465d41f669a861283f6` and its actual receipt,
whose sole parent is S2 `661e11b848102faf143bf5f4dc173e5ab7bcafdd`.
The [S3 checklist](s54.6.3-checklist.md) and Phase 54.6 are CLOSED. All five
original Phase obligations, all three native Steps and all four MMD outcomes
are accepted. The sole independent full Phase review covered all 21 accepted
paths and all seven whole programs against the original Phase base.
The sole independent full Phase review passed with no Current Phase Blockers,
Hygiene or Development Candidates. Ordinary `sbt --batch test` passed 2868
tests in 201 suites, zero failures/aborted suites, 9 pre-existing optional/deferred
canceled cases. SBT and wrapper exited zero; the serial lock was released.
The distinct parent manual local release binds `P546-CLOSURE-001@1`; its native
commit and closure receipt are verified after committing these final records.
The nine existing cancellations comprise four optional Docker Remotion opt-in
cases and five CV-04/CV-05/CV-06/SP-01 ownership cases; all 33 current Causal
behaviors succeeded. Phase 54.7 remains PLANNED and unstarted, owning all-family
Cozy supplier/fixture closure; external CNCF/Textus CBD runtime/UI integration
and acceptance retain their owners. Scala 2.12.18 and Cozy 0.3.3-SNAPSHOT are unchanged.
Behavior authority is the [supplier spec](../spec/cml-causal-metadata.md) and
[design](../design/cml-causal-metadata.md).

## Closure criteria

- Each demonstrated causal traversal is source-grounded and stable-ID based.
- One view cannot infer a causal edge merely because labels appear related.
- Unsupported Event Storming strength is explicit absence rather than a
  synthetic Cozy abstraction.

## Non-goals

- Creating Event Storming as a new CML language or Cozy runtime model.
- Inferring undeclared policy, cause, reaction, or external-system relations.
- Final consumer-contract closure, which belongs to Phase 54.7.

## References

- [Phase 54.5](phase-54.5.md)
- [Phase 54.6 checklist](phase-54.6-checklist.md)
- [Phase 54.7](phase-54.7.md)
