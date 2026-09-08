# CML Logical-Action UnitOfWork Compiler

status=accepted
phase=47.2.1
slice=P4721-LACG-01
updated_at=2026-09-08

## Authority and purpose

This document defines the Cozy-only producer contract for the versioned
`cozy.cml.logical-action-program.v1` generated ABI. It consumes existing,
normalized Composite StateMachine logical Actions, their Action occurrences,
and their Phase 47.1 metadata. It produces a deterministic descriptor and a
consumer-compiled binding surface to CNCF's existing
`ExecProgram[A] = Program[UnitOfWorkOp, A]` path.

The ABI is additive. It neither modifies nor replaces
`cozy.cml.composite-statemachine.v1`, its bootstrap or definitions,
`cozy.cml.composite-statemachine-projection.v1`, or
`cozy.cml.action-producer-metadata.v1`.

## Generated producer ABI

The schema version is exactly:

```text
cozy.cml.logical-action-program.v1
```

Both Cozy Scala generation routes emit these additive sources:

```text
target/scala-3.3.8/src_managed/main/scala/domain/composite/statemachine/actionprogram/
  CompositeStateMachineActionProgramAbi.scala
  LogicalActionCompiler.scala
  CompositeStateMachineActionProgramBootstrap.scala
  <definition>ActionProgram<n>.scala
```

The canonical sidecar is:

```text
target/cozy/cml-logical-action-program.json
```

The ABI contains versioned definition descriptors, logical Action descriptors,
and ordered occurrence descriptors. It has no executable-intent algebra of its
own. The generated `LogicalActionCompiler` is compiled in the consumer context
and receives only consumer-supplied `ExecProgram[Unit]` fragments.

## Identity, provenance, and metadata

Logical Action identity remains the existing CML `actionId`; it is not
re-authored by this ABI. Each occurrence is separately retained, even when it
refers to the same logical Action. An occurrence has an additive deterministic
`occurrenceId`, a one-based ordinal, definition and Action identity, operation
service/name/input type, CML input binding, Action and occurrence source
identity, origin, causal transition identity, placement, and applicable
role/from/to/on provenance.

The generator orders constituent occurrences by their causal transition in
normalized declaration order and, within each transition, by `exit`,
`transition`, then `entry`. Derived occurrences follow those constituent
occurrences in normalized declared order. No occurrence is deduplicated.

Every successful compiled occurrence carries non-optional metadata:

- `effectClass` distinguishes the existing `LOCAL` and `EXTERNAL` producer
  meanings;
- `transactionRequirement` preserves `REQUIRED` or
  `OUTSIDE_UNIT_OF_WORK` as an admission input, not a runtime decision;
- `idempotency` preserves its required flag and optional stable key reference;
  and
- `compensationHandlerRef` is the optional opaque application handler identity
  already associated with the logical Action.

Legacy Actions without metadata remain valid CML descriptors. They are not
compiled: the generated compiler reports `MissingMetadata` for a selected
occurrence rather than inventing metadata or silently excluding it.

## Consumer binding and composition contract

A binding names an existing logical `actionId`, repeats its resolved operation
and CML input binding for compatibility validation, and supplies an existing
`ExecProgram[Unit]`. The compiler returns validation values for unknown,
duplicate, missing, or operation/input-incompatible bindings; unknown or
duplicate occurrence selections; and missing selected metadata.

Selected occurrence identifiers are interpreted against the generated
definition. Accepted selected occurrences are composed in the definition's
causal order, not deduplicated, by sequentially composing the supplied
existing Free programs. The compiler starts with the existing
`Free.pure[UnitOfWorkOp, Unit](())` identity and uses `flatMap` sequencing. It
does not serialize executable programs; the JSON sidecar serializes descriptors
only.

## Ownership boundary and non-goals

Cozy owns normalized CML consumption, deterministic descriptor generation,
producer-side validation semantics, and this versioned handoff. CNCF Phase
64.2 UTP-02 owns compilation/admission of the emitted source and all consumer
runtime behavior.

This ABI does not introduce an `ActionOp`, callback, provider handle, script,
transaction manager, retry/recovery policy, compensation execution, or a Cozy
dependency on the full CNCF runtime. It does not change CML grammar/parser
behavior, extend `UnitOfWorkOp`, mutate CNCF, or claim consumer admission.
