# CML WORKFLOW Source Lowering

status=accepted
phase=62
slice=WFL-62-01A
updated_at=2026-09-17

## Decision

`WORKFLOW` is admitted as a first-class CML root through a small source wrapper
over the established `CompositeStateMachineDefinition`. The wrapper owns only
Workflow source identity, opaque version metadata, and declared required
capability boundaries. It delegates all state configuration and logical Action
meaning to the existing Composite StateMachine normalizer.

```
WORKFLOW root + definition
        |
        +-- root and definition source correlation
        +-- opaque direct version metadata
        +-- REQUIRED-OPERATION capability -> existing Action references
        |
        `-- one COMPOSITE-STATEMACHINE -> CompositeStateMachineDefinition
```

This keeps the Literate Model layers separate. Structural headings define the
small Workflow source structure, direct metadata gives machine-readable version
information, and prose/nonstructural headings remain document context rather
than hidden state-machine input.
A direct `REQUIRED-OPERATION` section without a sibling
`COMPOSITE-STATEMACHINE` is therefore a malformed structural declaration and
receives a syntax error instead of being treated as prose or dropped.

## Consequences

The implementation does not create Workflow states, transitions, guards,
Action variants, or another state-machine IR. A Workflow Action reference is
the existing CSM logical Action reference, so existing Operation input and
Action validation remain authoritative.

The wrapper records both the top-level `WORKFLOW` source identity and the
structural Workflow-definition identity. That correlation gives later phases a
stable diagnostic and consumer boundary without changing the CSM child source
identity or generated surfaces.

`REQUIRED-OPERATION` intentionally stops at a required capability declaration.
It says that one existing `OPERATION` Action is required; it does not bind a
provider or decide invocation, orchestration, continuation, control, retry, or
runtime policy.

## Rejected alternatives

- Treating every heading under `WORKFLOW` as structural would make narrative
  prose silently executable and violate the CML Literate Model.
- Reusing `WORKFLOW` as an alias for a direct CSM root would lose source-form
  identity, direct version metadata, and the capability boundary.
- Creating a parallel Workflow state/transition model would duplicate CSM
  semantics and require generator or runtime decisions outside this slice.
- Giving `REQUIRED-OPERATION` provider or execution-mode fields would preempt
  StateMachine SPI, ActionExecution, and continuation decisions owned by later
  phases.

## Deliberate non-goals

This lowering adds no Modeler generator wiring, generated source, StateMachine
API/SPI, provider binding, InvocationBinding, ActionExecution, Continuation,
WorkflowRun, persistence, REST/proxy/UI/Flutter surface, runtime store, or
execution engine.
