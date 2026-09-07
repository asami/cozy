# CML Action Compensation Handler Binding

status=accepted
phase=47.1
slice=ACTX-03
updated_at=2026-09-08

## Authority and boundary

This document is the accepted ACTX-03 design authority for the producer-only
association between CML Action metadata and an application compensation
handler. It defines the association's meaning. ACTX-04 now parses and
statically validates the authored association into normalized source metadata;
Cozy does not resolve a handler implementation, generate runtime output,
execute, or prove it here.

The optional `compensationHandlerRef` is an opaque reference bound to the
existing logical `actionId`. It is not bound to an `ActionOccurrence` and does
not create another Action identity. The reference is an application-owned,
stable symbolic handler identity. The association does not re-author the
Action and does not expose handler body, provider, or execution semantics.

The authoritative [CML Composite StateMachine Action Algebra](cml-composite-statemachine-action-algebra.md)
continues to own logical Action identity and occurrence provenance. The
authoritative [CML Action Producer Metadata](cml-action-producer-metadata.md)
continues to own the surrounding producer metadata contract.

## Occurrence relationship

All Action occurrences retain their own causal provenance, including occurrence
identity, logical Action identity, origin, causal transition, placement, and
source identity. When later consumer metadata carries the action-level
`compensationHandlerRef`, a consumer may attribute actual completed external
effects to individual occurrences using that preserved provenance.

That attribution does not claim that an occurrence was executed, committed, or
compensated. The association is semantic producer metadata, not an execution
receipt or runtime outcome.

## Applicability boundary

Business compensation is meaningful only after the consumer runtime reports a
completed external effect outside an admitted atomic UnitOfWork. The presence
of a handler reference never makes an `EXTERNAL` effect technically rollbackable.
An unavailable `REQUIRED` admission remains consumer reject behavior; the
requirement must not be silently weakened into best-effort execution.

This authority does not define handler existence checks, implementation
resolution diagnostics, execution ordering, retry or recovery policy, or a
runtime success/failure result. ACTX-04 validates the direct authored field,
its nonempty opaque reference, and the `EXTERNAL` plus `OUTSIDE_UNIT_OF_WORK`
applicability boundary without resolving the handler.

## Future deterministic representation intent

The future representation carries at most one optional
`compensationHandlerRef` per Action metadata record, together with the existing
logical `actionId` and the derived occurrence ordering/provenance. It remains
an additive metadata association and does not introduce a second Action,
handler, transaction, or Saga algebra.

The following work remains deferred to its owning boundary:

- ACTX-05 owns deterministic generation of the metadata, references, ordering
  representation, and generated IR/ABI/output binding;
- `goldenport-cncf` Phase 64.1 owns UnitOfWork and optional-2PC admission,
  rollback, compensation-handler execution, and durable `RecoveryRequired`
  runtime proof; and
- automatic Saga or reverse-order chains, handler body/provider semantics, and
  retry/recovery policy remain outside this association and are not inferred by
  it.

ACTX-04 has completed parser acceptance and source-level association
validation. Generated representation, ordering output, handler execution,
automatic Saga/reverse chains, retry/recovery policy, and runtime proof remain
deferred exactly to their owning boundaries. The current [CML Composite
StateMachine Grammar and Validation](cml-composite-statemachine-grammar-validation.md)
authority includes the direct compensation field and its static applicability
check.

## Non-goals

- No second Action, handler, transaction, or Saga algebra.
- No compensation handler body, provider contract, or execution semantics in
  CML metadata.
- No claim of Cozy runtime acceptance, rollback, execution, compensation, or
  recovery proof.
- No change to legacy Action validity or to existing Action occurrence
  provenance.
