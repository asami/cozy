# Phase 54.6: Event Storming Causal Traversal

Status: IN_PROGRESS

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
| MMD-546-01 | Publish admitted Actor, Command/Operation, Aggregate/Entity, Event, cause/consequence, and affected-subject links. | IN_PROGRESS |
| MMD-546-02 | Publish declared Workflow policy/reaction and subsequent action/event links where modeled. | IN_PROGRESS |
| MMD-546-03 | Include external Component/dependency and Query/View/read-model links only where admitted. | IN_PROGRESS |
| MMD-546-04 | Freeze causal fixtures and handoff for final CBD Support publication/fixture acceptance. | PLANNED |

## Stage: Derived causal supplier and traversal

Stage Status:
- Current status: IN_PROGRESS
- Owner: Cozy / Phase 54.6 parent
- Update rule: Update from actual Step validation, review and commit receipts; closure is based on the [Phase checklist](phase-54.6-checklist.md).

| Step | Outcome | Status |
| --- | --- | --- |
| [S54.6.1](s54.6.1.md) | Derived supplier, strict v1 publication and paired executable specification. | CLOSED |
| [S54.6.2](s54.6.2.md) | Qualified direct causal/context traversal over admitted Graph. | IN_PROGRESS |
| S54.6.3 | Fixed declared/absence JSON resources, peer compatibility and final handoff. | PLANNED |

- [x] S54.6.1 supplier accepted by focused validation, independent review and local Step commit.
- [ ] S54.6.2 traversal accepted by its selected evidence and local Step commit.
- [ ] S54.6.3 fixtures and final supplier handoff accepted by selected evidence and local Step commit.
- [ ] All Phase obligations, sole full review, ordinary full tests and distinct release/native audit complete.

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
Native local Step commit and its receipt remain pending under the
[S2 checklist](s54.6.2-checklist.md); S2 remains IN_PROGRESS and S54.6.3 remains
PLANNED. Final full
Phase review, full validation, distinct release and native audit remain pending.
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
