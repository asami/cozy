# Phase 54.6: Event Storming Causal Traversal

Status: PLANNED

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
Actor
  -> Command / Operation
  -> Aggregate / Entity
  -> Domain Event
  -> Workflow Rule / Policy / Reaction
  -> subsequent Command / Operation / Event
```

Every edge is published only when modeled or faithfully derivable from
admitted semantic IR. Missing causes, effects, reactions, external systems,
or read-model links remain explicit gaps.

## In-scope work

| ID | Outcome | Status |
| --- | --- | --- |
| MMD-546-01 | Publish admitted Actor, Command/Operation, Aggregate/Entity, Event, cause/consequence, and affected-subject links. | planned |
| MMD-546-02 | Publish declared Workflow policy/reaction and subsequent action/event links where modeled. | planned |
| MMD-546-03 | Include external Component/dependency and Query/View/read-model links only where admitted. | planned |
| MMD-546-04 | Freeze causal fixtures and handoff for final CBD Support publication/fixture acceptance. | planned |

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
