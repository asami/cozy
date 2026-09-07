# CML Action Producer Metadata

status=accepted
phase=47.1
slice=ACTX-02
updated_at=2026-09-08

## Authority and boundary

This document is the accepted ACTX-02 design authority for the v1 producer-side
semantic metadata contract of a CML Action. It freezes the meaning and future
authored surface of the metadata; it does not claim that Cozy currently parses,
validates, generates, executes, or proves this contract.

Cozy remains the producer. The consumer runtime owns UnitOfWork admission,
provider and optional 2PC selection, execution, rollback, compensation
execution, and durable recovery outcomes. A compensation reference never makes
an `EXTERNAL` effect rollbackable by itself.

## Frozen v1 ActionMetadata contract

The future producer contract is the following small semantic surface:

```text
ActionMetadata
  actionId
  effectClass
  transactionRequirement
  idempotency
  compensationHandlerRef?
  ordering / provenance
```

The fields have these exact meanings and values:

- `actionId` is the existing logical Action identity. Producer metadata uses
  that identity and never re-authors it.
- `effectClass` is exactly `LOCAL` or `EXTERNAL`. An `EXTERNAL` effect remains
  external even when an application compensation handler reference is present.
- `transactionRequirement` is exactly `REQUIRED` or
  `OUTSIDE_UNIT_OF_WORK`. `REQUIRED` requests admission to an atomic
  UnitOfWork boundary. Provider selection and optional 2PC selection belong
  exclusively to the consumer runtime. If the required admission is
  unavailable, the consumer must reject it rather than silently weaken the
  requirement.
- `idempotency` is exactly `NOT_REQUIRED` or `REQUIRED(keyRef)`. `keyRef` is a
  stable producer-side symbolic reference; it is not runtime state and does not
  encode retry policy.
- `compensationHandlerRef` is optional and is only an opaque, stable reference
  to an application handler. Its producer-only association meaning is frozen
  by the accepted [CML Action Compensation Handler Binding](cml-action-compensation-handler-binding.md)
  authority; parser/static validation and binding resolution remain deferred to
  ACTX-04.
- `ordering / provenance` is derived from existing constituent and derived
  Action occurrence data. It is not a separate authored field and is not an
  execution-order claim.

This contract does not introduce a second Action execution algebra. Existing
logical Action identity and occurrence provenance remain the source of the
producer-side references used by later work.

## Future additive authored surface

The future additive authored metadata names are:

```text
EFFECT
TRANSACTION
IDEMPOTENCY
IDEMPOTENCY-KEY
COMPENSATION-HANDLER
```

`EFFECT` supplies `effectClass`, `TRANSACTION` supplies
`transactionRequirement`, `IDEMPOTENCY` supplies `idempotency`, and
`IDEMPOTENCY-KEY` supplies the `keyRef` within `REQUIRED(keyRef)`.
`COMPENSATION-HANDLER` supplies the opaque `compensationHandlerRef`.

These names do not replace or re-author legacy Action identity. A legacy Action
with no Phase 47.1 metadata fields remains valid and unchanged. If any Phase
47.1 metadata field is present, `EFFECT`, `TRANSACTION`, and `IDEMPOTENCY` are
mandatory. `IDEMPOTENCY-KEY` occurs exactly when `IDEMPOTENCY=REQUIRED`; it is
not admitted with `NOT_REQUIRED` and is not omitted from a `REQUIRED(keyRef)`
declaration. `COMPENSATION-HANDLER` remains opaque in the authored surface. Its
producer-only association meaning is defined by the accepted [CML Action
Compensation Handler Binding](cml-action-compensation-handler-binding.md)
authority; parser acceptance, static validation, and binding resolution remain
deferred to ACTX-04.

This is an additive design surface only. ACTX-02 does not implement this
grammar, parser behavior, validation, or generated representation.

## Explicit non-goals

This authority does not define or authorize:

- retry, backoff, timeout, circuit-breaker, or provider settings;
- a second Action execution algebra;
- a handler body, automatic Saga reversal, compensation execution, or
  recovery implementation;
- an administrator workflow; or
- a Cozy runtime proof.

Runtime rollback, 2PC admission, handler execution or failure, and durable
`RecoveryRequired` remain consumer-owned Phase 64.1 responsibilities. Cozy
does not claim those outcomes from this producer metadata contract.

## Current implementation boundary and follow-up ownership

The accepted [CML Composite StateMachine Grammar and Validation](cml-composite-statemachine-grammar-validation.md)
authority continues to describe the currently implemented grammar. That
grammar does not currently parse or accept the authored metadata above; its
transaction, retry, and compensation metadata exclusion remains in force
until ACTX-04 implements that surface. ACTX-03 and ACTX-04 own the deferred
compensation association and validation decisions.

The existing [CML Composite StateMachine Action Algebra](cml-composite-statemachine-action-algebra.md)
continues to own logical Action identity and occurrence provenance. Later
generation and handoff work may carry this metadata to the consumer, but this
document changes no Scala, parser, generator, test, or runtime behavior.

The accepted [CML Action Compensation Handler Binding](cml-action-compensation-handler-binding.md)
authority defines the future action-level association without binding a
reference to an `ActionOccurrence` or exposing handler implementation. The
current [CML Composite StateMachine Grammar and Validation](cml-composite-statemachine-grammar-validation.md)
authority continues to exclude compensation syntax; ACTX-04 owns its later
parser/static validation and binding resolution, and ACTX-05 owns deterministic
generation.
