# Phase 47.1 - CML Action Transaction, Compensation Handler, and Recovery Semantics

Status: in progress
Planned at: 2026-09-05
Revised at: 2026-09-08
Parent: [Phase 47 - CML Composite StateMachine and Workflow Modeling](phase-47.md)
Cross-repository consumer: `asami/goldenport-cncf` Phase 64.1

## Responsibility and acceptance ownership

This Phase is a Cozy producer-side contract and handoff. Cozy owns CML
semantic action metadata, static validation, deterministic generated producer
contract evidence, and the handoff of that evidence. Cozy does not execute the
contract or prove consumer runtime behavior.

`asami/goldenport-cncf` Phase 64.1 alone owns the consumer runtime acceptance:
UnitOfWork execution, optional-2PC capability and admission behavior,
compensation execution, durable `RecoveryRequired` behavior, and the runtime
proof for those outcomes. Those are consumer-owned runtime requirements, not
observed Cozy behavior. Phase 47.2 remains outside this Phase.

## Purpose

Define the model-level information needed to compile CML StateMachine and
Composite StateMachine actions onto CNCF's existing Free × UnitOfWork execution
model while keeping business compensation explicit and application-owned.

The selected consumer execution contract is:

```text
CML logical actions
  -> compile to ExecProgram[UnitOfWorkOp, A]
  -> CNCF UnitOfWork
       failure before commit -> complete rollback
       commit -> external effects may follow
                  |
                  +-> explicit application compensation handler when required
                         failure -> CNCF durable RecoveryRequired event
                                    containing original UnitOfWorkId
```

CML must not become a transaction manager, Saga engine, or manual-recovery DSL.
The contract below records the future handoff semantics; it is not a claim that
Cozy currently executes or proves those runtime outcomes.

## Core Decisions

- Future Cozy producer metadata carries the semantic action identity, effect
  class, transaction requirement, idempotency requirement, optional
  compensation-handler reference, and derived ordering/provenance needed by a
  consumer; it does not add provider configuration or runtime execution. The
  exact ACTX-02 contract is frozen in [CML Action Producer Metadata](../design/cml-action-producer-metadata.md).
- The generated producer contract continues to align with the existing CNCF
  `UnitOfWorkOp` execution algebra; no parallel StateMachine-specific execution
  algebra is introduced by Cozy.
- UnitOfWork execution, complete atomic rollback, optional 2PC capability and
  admission, compensation execution, and durable `RecoveryRequired` behavior
  are owned and proved only by the Phase 64.1 consumer runtime.
- Effects outside a committed UnitOfWork are not modeled as technically
  rollbackable merely because a business reversal may exist; the consumer
  runtime owns the resulting execution policy.
- Business compensation is application logic implemented as an explicit
  compensation handler/program. Cozy may carry the stable association but does
  not synthesize the handler body or execute it.
- The business/manual recovery procedure after a durable recovery event is
  outside CML semantics and outside Cozy acceptance.
- Committed StateMachine transitions remain history. A business reversal is a
  new explicit transition/action, never history erasure.

## Minimal Action Metadata

For v1, keep Action metadata small and semantic. The exact producer-side
contract is the accepted [CML Action Producer Metadata](../design/cml-action-producer-metadata.md)
authority:

```text
ActionMetadata
  actionId
  effectClass
  transactionRequirement
  idempotency
  compensationHandlerRef?
  ordering / provenance
```

Interpretation:

- `actionId` is the existing logical Action identity and is never re-authored;
- `effectClass` is exactly `LOCAL` or `EXTERNAL`;
- `transactionRequirement` is exactly `REQUIRED` or `OUTSIDE_UNIT_OF_WORK`;
- `idempotency` is exactly `NOT_REQUIRED` or `REQUIRED(keyRef)`;
- `compensationHandlerRef` is an optional opaque stable application-handler
  reference; and
- ordering/provenance is derived from existing constituent/derived occurrence
  data rather than authored separately.

If any future metadata field is authored, `EFFECT`, `TRANSACTION`, and
`IDEMPOTENCY` are mandatory. `IDEMPOTENCY-KEY` occurs exactly with
`IDEMPOTENCY=REQUIRED`; `COMPENSATION-HANDLER` remains opaque until ACTX-03/04.
Legacy Actions with no Phase 47.1 metadata remain valid. This is a future
additive surface and does not claim current parser acceptance.

Retry counts, backoff, timeout, circuit breakers, transport tuning, and provider
transaction configuration are CNCF runtime policy rather than CML semantics.

A separate reversibility enum is not required for v1. Operationally:

```text
inside admitted UnitOfWork -> rollbackable by atomic transaction
outside UnitOfWork + handler -> application-compensatable
outside UnitOfWork + no handler -> no automatic business compensation
```

## Consumer-owned runtime contract: StateMachine atomicity

The following is a requirement on the Phase 64.1 consumer runtime, not a
Cozy-local runtime observation or acceptance result.

For an admitted UnitOfWork:

```text
select transition
  -> candidate state
  -> compile/compose logical actions
  -> ExecProgram[UnitOfWorkOp, A]
  -> UnitOfWork interpretation
  -> persist state/local effects
  -> commit
```

Any pre-commit error causes complete rollback:

```text
failure
  -> no partial local commit
  -> no successful CommittedTransition occurrence
```

This is the default technical consistency mechanism and is preferred over
compensation whenever the effects can participate in the same atomic boundary.

## Consumer-owned runtime contract: optional 2PC semantics

When the model requires distributed atomicity, generated metadata must allow
CNCF to check whether all required participants can join an admitted 2PC or
equivalent atomic protocol.

If not, admission fails. The model is never silently weakened into best-effort
execution.

CML does not contain transaction-manager or resource-manager configuration.

## Producer metadata and consumer-owned compensation semantics

A compensation handler is an implementation-level application program associated
with a logical external action/effect.

Conceptually:

```text
logical action: reserveShipment
compensationHandlerRef: cancelShipmentReservation
```

The handler itself should execute through normal CNCF facilities and can compile
or return an `ExecProgram[UnitOfWorkOp, CompensationResult]`.

CML/Cozy responsibilities are limited to future stable semantic association,
static validation, deterministic generation, and handoff of the producer
contract. The actual business recovery algorithm and its execution belong to
application code and the Phase 64.1 consumer runtime.

No automatic reverse-order Saga chain is required by v1. Applications may
compose their own compensation programs where appropriate.

## Consumer-owned runtime contract: compensation failure / recovery boundary

CML must assume that compensation can fail.

The Phase 64.1 consumer contract is:

```text
compensation handler failure
  -> durable RecoveryRequired
       unitOfWorkId = original committed UoW
       + correlation/causation and occurrence references
```

CML does not define how an administrator repairs the business situation. It may
provide human-readable documentation/reference metadata, but recovery procedure
semantics are owned by the application/operations layer. Cozy does not claim to
have observed or proved this behavior.

## Future producer contract: Composite StateMachine interaction

Constituent and composite actions compile to the same existing UnitOfWork
program model and preserve deterministic causal ordering/provenance.

Whether actions can share one UnitOfWork depends on the admitted execution plan
and runtime capabilities. External effects are not moved inside an atomic
boundary merely to simplify the model.

## Future Cozy producer static analysis

Cozy/SimpleModeler should detect, where possible:

- unknown action identities;
- unresolved compensation handler references where declared;
- duplicate/conflicting logical actions at constituent/composite levels;
- incompatible transaction requirements;
- unsafe idempotency declarations on retryable/recovery paths;
- external effects whose failure path has neither explicit application
  compensation nor an acknowledged manual-recovery boundary;
- ordering/provenance cycles; and
- model metadata that incorrectly implies rollback of an external committed
  effect.

Provider capability remains a CNCF admission concern.

## Work Stack

| ID | Stage | Outcome | Status |
| --- | --- | --- | --- |
| ACTX-01 | Existing execution inventory | Current Cozy CML actions, generated projections, and the producer/consumer evidence boundary are inventoried. | completed |
| ACTX-02 | Minimal metadata contract | [`CML Action Producer Metadata`](../design/cml-action-producer-metadata.md) freezes the v1 producer-side `actionId`, effect class, transaction requirement, idempotency, compensation handler reference, and derived ordering/provenance contract. | completed |
| ACTX-03 | Compensation handler binding | Stable CML/generated association to application compensation handler is defined without embedding handler implementation. | planned |
| ACTX-04 | Static validation | Handler resolution, transaction consistency, idempotency, external-effect recovery boundary, and ordering checks are implemented where tractable. | planned |
| ACTX-05 | Generation | Metadata and handler references compile deterministically alongside the existing UnitOfWork program binding. | planned |
| ACTX-06 | Cozy producer handoff | Cozy records and hands off the future producer contract; consumer runtime ownership is explicit. | planned |
| ACTX-07 | Producer-contract acceptance | Cozy verifies producer-contract evidence and handoff completeness; runtime proof is explicitly excluded and remains a Phase 64.1 consumer obligation. | planned |

## Acceptance ownership and evidence boundary

- Cozy acceptance for this Phase is limited to current-source inventory,
  producer metadata/validation/generation work, deterministic producer-contract
  evidence, and the explicit handoff boundary.
- The future producer contract preserves the semantic requirements for atomic
  UnitOfWork execution, optional distributed atomicity admission, explicit
  application compensation, and recovery escalation without leaking provider
  policy into CML.
- Local rollback, optional 2PC admission, compensation success/failure, durable
  `RecoveryRequired`, and runtime proof are consumer-owned Phase 64.1 outcomes;
  no Cozy document or acceptance record claims that Cozy proves them.
- CML does not own the administrator/manual recovery procedure.
- Committed transitions remain immutable historical facts as a consumer contract.
- Minimal Action metadata is intended to survive future generation without
  provider-specific runtime policy leakage.

## Non-Goals

- A second Action execution algebra beside `UnitOfWorkOp`.
- Automatic Saga compensation-chain synthesis.
- Embedding compensation handler implementation code in CML.
- Embedding JTA/XA/provider configuration in CML.
- Modeling manual administrator recovery workflow in CML core semantics.
- Rewriting committed transition history.
- Phase 47.2 work or any other phase/shared strategy projection.

## References

- `docs/phase/phase-47.md`
- `docs/notes/cml-action-transaction-compensation-proposal.md`
- `docs/phase/phase-47-checklist.md` (closed Cozy producer-handoff precedent)
