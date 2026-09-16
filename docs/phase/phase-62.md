# Phase 62 - First-Class CML WORKFLOW Language and Producer ABI

Status: planned
Planned at: 2026-09-16
Depends on: Phase 47 and Phase 47.2.2 Cozy producer closure
Cross-repository consumer: `asami/goldenport-cncf` Phase 77

## Purpose

Make `WORKFLOW` a first-class CML declaration and generate one stable typed
producer ABI for downstream runtimes.

`WORKFLOW` is not a second state-transition language. Its structural semantics
normalize to the accepted StateMachine / Composite StateMachine model; the new
language surface supplies the explicit Workflow identity and progression-boundary
contract that a runtime cannot safely infer from names, comments, or Action
metadata alone.

The canonical producer path is:

```text
CML WORKFLOW
  -> Cozy parse / normalize / validate
  -> Composite StateMachine reuse + WorkflowDefinition
  -> generated Workflow ABI + ComponentFactory bootstrap metadata
  -> CNCF Phase 77 admission
```

## Ownership Boundary

- Cozy owns CML syntax, semantic normalization, static validation, stable
  source identity, generated ABI, and producer fixtures.
- CNCF Phase 77 owns generated-ABI admission, ComponentFactory integration,
  the independent WorkflowInstance persistence contract, and the reusable
  runtime progression contract.
- `sm-workflow` owns Textus-local `advance`, WorkflowRun / WorkOrder state,
  SQLite, leases, skill packaging, and public CLI/server behavior. None of
  those runtime concerns are implemented in this Phase.

An entity-local StateMachine and a Workflow definition are different CML
authorities. The former governs the entity's own lifecycle and entity-owned
persisted state. `WORKFLOW` declares a separately identified process model; it
does not add workflow fields to an entity or make an entity record the durable
store for WorkflowInstance state.

## Work Stack

| ID | Outcome | Status |
| --- | --- | --- |
| WFL-62-01 | Freeze the `WORKFLOW` root/profile contract, its relationship to Composite StateMachine, stable identity/versioning, and explicit progression-boundary semantics. | planned |
| WFL-62-02 | Add parser, normalized model, and static validation for `WORKFLOW`, including rejection of ambiguous automatic progression and unsupported raw execution surfaces. | planned |
| WFL-62-03 | Generate a versioned typed WorkflowDefinition ABI and direct ComponentFactory bootstrap metadata, preserving source identities, transition/action provenance, typed Operation references, and declared progression boundaries. | planned |
| WFL-62-04 | Prove deterministic producer output from real CML fixtures and freeze the exact CNCF Phase 77 handoff. | planned |

## Required Contract Decisions

WFL-62-01 must decide and document a closed generated progression category.
At minimum it distinguishes:

- an `automatic` transition, evaluable solely from persisted workflow state
  under declared deterministic constraints; and
- a semantic boundary, represented as a typed Work Order, Decision, or Wait
  requirement rather than an arbitrary command or opaque callback.

An automatic transition may not be inferred from Action names, effect labels,
or a consumer default. The accepted contract must state which declared guards,
operations, and effects are legal on an automatic transition and must reject
anything outside that closed set.

The resulting WorkflowDefinition must reuse the Phase 47 Composite
StateMachine identities, transition semantics, and typed logical Action /
Operation path. It must not duplicate those concepts under Workflow-only names.

## Completion Conditions

- A real `WORKFLOW` CML source parses, normalizes, validates, and generates
  deterministically without a parallel handwritten runtime definition.
- Generated metadata exposes Workflow identity/version, the reused composite
  structure, source locations, typed Operation references, and every declared
  progression boundary.
- The generated definition separates declared Workflow identity from runtime
  WorkflowInstance identity. It supplies neither an entity-field persistence
  shortcut nor a second source of truth for entity StateMachine state.
- Multiple eligible automatic transitions and unsupported automatic behavior
  fail validation or admitted runtime evaluation with structured diagnostics;
  they never acquire an implicit priority.
- Semantic boundaries are explicit in generated ABI and cannot be crossed by a
  consumer solely through model inference.
- The existing `cozy.cml.logical-action-program.v1` producer surface remains
  the executable Action path; this Phase introduces no second Action algebra.
- The exact generated ABI/version and real-source fixture are recorded for
  CNCF Phase 77; no CNCF or Textus runtime acceptance is claimed here.

## Non-Goals

- A BPMN/DAG language, arbitrary scripting, raw shell commands, or opaque
  callbacks in CML.
- `advance`, WorkflowRun persistence, WorkOrder leasing, SQLite, retry,
  recovery, scheduler implementation, or Codex skill execution.
- A Workflow-specific replacement for StateMachine, Composite StateMachine,
  `UnitOfWorkOp`, or the Phase 47 logical Action program.
- CNCF ComponentFactory/runtime implementation or external consumer acceptance.

## References

- [Phase 47](phase-47.md)
- [Phase 47.2.2](phase-47.2.2.md)
- [Phase 62 Checklist](phase-62-checklist.md)
- `asami/goldenport-cncf/docs/phase/phase-77.md`
