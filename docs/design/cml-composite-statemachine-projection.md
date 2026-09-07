# CML Composite StateMachine Projection Metadata

Status: accepted
Scope: Phase 47 CSM-09 deterministic projection metadata
Implemented at: 2026-09-07

## Purpose

CSM-09 defines one pure Cozy-side projection of the normalized CSM-07/08
Composite StateMachine definition IR. It produces a deterministic,
versioned JSON document for a later visualization, diagnostics, or review
consumer. CML remains the semantic authority and no consumer is admitted by
this producer-side document.

## Generated Document Contract

Both `modeler-scala` and `modeler-scala-value` always write the exact path:

```text
target/cozy/composite-statemachine-projection.json
```

The document has the exact schema version:

```text
cozy.cml.composite-statemachine-projection.v1
```

Its top-level JSON object has fixed key order:

1. `schemaVersion`
2. `definitions`

`definitions` preserves CSM-07 normalized declaration order. An input with no
Composite StateMachine definitions emits the same versioned document with
`definitions: []`; it does not omit the document.

Each definition preserves, in fixed object-key and vector order:

- definition `identity`, `name`, and source `line`;
- ordered constituents with `role`, StateMachine reference, declared direct
  state universe, optional typed subject, and source;
- declared composite states and sources;
- derivation identity, selected composite state, complete declared
  role/state configuration, and sources;
- optional initial configuration in normalized constituent-role order;
- declared typed logical actions, including `OPERATION` service/operation
  reference, optional input type and CML input binding, and source;
- constituent action occurrences with identity, role, from/to/on, placement,
  typed logical action/Operation, and source; and
- derived action occurrences with declared derived-transition reference,
  from/to, typed logical action/Operation, and source.

`null` represents a missing optional value, including a source line omitted by
the parser. JSON strings are escaped by the projection renderer. The renderer
uses only fixed `Vector` order and fixed object field vectors; it does not use
maps or unordered traversal.

## Interpretation Boundary

This document reports declared derivation matching configurations only. It does
not evaluate a configuration, report a runtime rule-match result, choose a
derivation, or reinterpret CML semantics. It preserves a derived action's
declared `derivedTransition` reference only; it does not synthesize a graph or
claim reachability, liveness, causation beyond the normalized declaration, or
action execution.

The projection is additive to the ordinary Scala output, CSM-07 generated ABI,
and CSM-08 bootstrap source. `ComponentApiContractMetadata` and
`target/cozy/component-api-model.json` retain their existing schema and
ownership unchanged.

## Explicit Deferrals

CSM-09 does not add a visualization or Graphviz layout, meta API, CBD Support
or BoK consumer, diagnostics policy, SimpleModeler or CNCF change, Component
API schema change, runtime admission/interpreter, reflection/resource lookup,
Workflow syntax or profile, service endpoint, persistence, transaction,
retry, concurrency, lifecycle policy, or action execution. Cross-repository
consumer acceptance remains CSM-10 work.
