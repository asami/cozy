# CML Action Producer Metadata Generation

Status: accepted
Scope: Phase 47.1 ACTX-05 only
Implemented at: 2026-09-08

## Purpose

ACTX-05 projects validated, normalized Composite StateMachine Action metadata
into a separate deterministic producer surface. It does not extend the existing
Composite StateMachine ABI or projection document, and it does not implement a
consumer runtime.

The Scala and JSON schema version is exactly:

```text
cozy.cml.action-producer-metadata.v1
```

## Generated producer surface

Both `modeler-scala` and `modeler-scala-value` generate these additive Scala
sources beside the existing Composite StateMachine source tree:

```text
target/scala-3.3.8/src_managed/main/scala/domain/composite/statemachine/actionproducer/
  CompositeStateMachineActionProducerMetadataAbi.scala
  CompositeStateMachineActionProducerMetadataBootstrap.scala
  <definition>ActionProducerMetadata<n>.scala
```

The ABI exposes `Idempotency(required, keyRef)` and `ActionMetadata` records.
Each record joins the existing `definitionIdentity` and logical `actionId`; it
does not create a replacement identity or an Action occurrence. The bootstrap
is the deterministic discovery surface over the emitted records.

`ScalaGenerator` also writes the canonical JSON sidecar at:

```text
target/cozy/cml-action-producer-metadata.json
```

The JSON document has `schemaVersion` and an ordered `definitions` array. Each
emitted Action record contains `actionId`, `effectClass`,
`transactionRequirement`, `idempotency.required`, `idempotency.keyRef`, and
the optional opaque `compensationHandlerRef`.

## Ordering and compatibility invariants

- Only logical Actions with normalized source metadata are emitted.
- Definitions and Actions preserve their existing normalized declaration order.
- A legacy Action without metadata yields no producer-metadata record.
- Repeating either public Scala route produces byte-identical producer output.
- `cozy.cml.composite-statemachine.v1`, its bootstrap, generated definition
  `definition`, logical Action identity, action-occurrence provenance, and
  `target/cozy/composite-statemachine-projection.json` remain unchanged.

## Explicit non-goals

ACTX-05 does not add a runtime, UnitOfWork interpretation, provider or 2PC
selection, handler resolution or execution, recovery behavior, retry policy,
or Phase 47.2 work. Those consumer-owned concerns remain outside Cozy's
producer-metadata generation boundary.
