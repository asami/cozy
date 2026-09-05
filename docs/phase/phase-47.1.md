# Phase 47.1 - CML Action Transaction, Compensation Handler, and Recovery Semantics

Status: planned
Planned at: 2026-09-05
Revised at: 2026-09-05
Parent: [Phase 47 - CML Composite StateMachine and Workflow Modeling](phase-47.md)
Cross-repository consumer: `asami/goldenport-cncf` Phase 64.1

## Purpose

Define the model-level information needed to compile CML StateMachine and
Composite StateMachine actions onto CNCF's existing Free × UnitOfWork execution
model while keeping business compensation explicit and application-owned.

The selected execution contract is:

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

## Core Decisions

- CML logical actions compile onto the existing CNCF `UnitOfWorkOp` execution
  algebra; no parallel StateMachine-specific execution algebra is introduced.
- Actions that participate in one CNCF UnitOfWork inherit its complete atomic
  rollback semantics.
- 2PC is optional runtime capability for enlarging an atomic boundary; CML may
  express a logical atomicity requirement but does not name JTA/XA/provider
  mechanics.
- Effects outside the committed UnitOfWork are not modeled as technically
  rollbackable merely because a business reversal may exist.
- Business compensation is application logic implemented as an explicit
  compensation handler/program registered for a logical action/effect.
- CML may carry the stable reference/identity needed to associate an action with
  its compensation handler, but does not synthesize the handler body.
- A compensation handler may fail. CNCF then durably emits/persists a
  `RecoveryRequired` event containing the original `UnitOfWorkId`.
- The business/manual recovery procedure after that durable event is outside
  CML semantics.
- Committed StateMachine transitions remain history. A business reversal is a
  new explicit transition/action, never history erasure.

## Minimal Action Metadata

For v1, keep Action metadata small and semantic:

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

- `actionId`: stable logical action identity;
- `effectClass`: at minimum distinguishes local versus externally visible
  effect where this is part of model meaning;
- `transactionRequirement`: logical atomicity requirement, without provider
  configuration;
- `idempotency`: semantic requirement/key contract needed for safe re-execution;
- `compensationHandlerRef`: optional stable application handler reference for an
  external effect requiring business reversal;
- `ordering/provenance`: causal placement and constituent/composite source.

Retry counts, backoff, timeout, circuit breakers, transport tuning, and provider
transaction configuration are CNCF runtime policy rather than CML semantics.

A separate reversibility enum is not required for v1. Operationally:

```text
inside admitted UnitOfWork -> rollbackable by atomic transaction
outside UnitOfWork + handler -> application-compensatable
outside UnitOfWork + no handler -> no automatic business compensation
```

## StateMachine Atomicity

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

## Optional 2PC Semantics

When the model requires distributed atomicity, generated metadata must allow
CNCF to check whether all required participants can join an admitted 2PC or
equivalent atomic protocol.

If not, admission fails. The model is never silently weakened into best-effort
execution.

CML does not contain transaction-manager or resource-manager configuration.

## Compensation Handler Semantics

A compensation handler is an implementation-level application program associated
with a logical external action/effect.

Conceptually:

```text
logical action: reserveShipment
compensationHandlerRef: cancelShipmentReservation
```

The handler itself should execute through normal CNCF facilities and can compile
or return an `ExecProgram[UnitOfWorkOp, CompensationResult]`.

CML/Cozy responsibilities are limited to stable semantic association and
validation that a required reference resolves in the generated/application
contract. The actual business recovery algorithm belongs to application code.

No automatic reverse-order Saga chain is required by v1. Applications may
compose their own compensation programs where appropriate.

## Compensation Failure / Recovery Boundary

CML must assume that compensation can fail.

The CNCF contract is:

```text
compensation handler failure
  -> durable RecoveryRequired
       unitOfWorkId = original committed UoW
       + correlation/causation and occurrence references
```

CML does not define how an administrator repairs the business situation. It may
provide human-readable documentation/reference metadata, but recovery procedure
semantics are owned by the application/operations layer.

## Composite StateMachine Interaction

Constituent and composite actions compile to the same existing UnitOfWork
program model and preserve deterministic causal ordering/provenance.

Whether actions can share one UnitOfWork depends on the admitted execution plan
and runtime capabilities. External effects are not moved inside an atomic
boundary merely to simplify the model.

## Static Analysis

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
| ACTX-01 | Existing execution inventory | Current CML actions and CNCF Free/`UnitOfWorkOp`/UnitOfWork semantics are inventoried. | planned |
| ACTX-02 | Minimal metadata contract | `actionId`, effect class, transaction requirement, idempotency, compensation handler reference, and ordering/provenance are frozen. | planned |
| ACTX-03 | Compensation handler binding | Stable CML/generated association to application compensation handler is defined without embedding handler implementation. | planned |
| ACTX-04 | Static validation | Handler resolution, transaction consistency, idempotency, external-effect recovery boundary, and ordering checks are implemented where tractable. | planned |
| ACTX-05 | Generation | Metadata and handler references compile deterministically alongside the existing UnitOfWork program binding. | planned |
| ACTX-06 | CNCF handoff | Generated contracts align with CNCF Phase 64.1 complete rollback and RecoveryRequired semantics. | planned |
| ACTX-07 | Acceptance | Real CML proves local rollback, optional 2PC admission, handler success/failure, and durable recovery escalation. | planned |

## Acceptance

- Local StateMachine actions plus state mutation inherit complete CNCF UnitOfWork
  rollback on pre-commit failure.
- Required distributed atomicity cannot silently downgrade.
- External business compensation is represented by an explicit application
  compensation handler reference, not an automatically synthesized inverse.
- Compensation handler failure is expected and maps to CNCF durable
  `RecoveryRequired` with original `UnitOfWorkId`.
- CML does not own the administrator/manual recovery procedure.
- Committed transitions remain immutable historical facts.
- Minimal Action metadata survives generation without provider-specific runtime
  policy leakage.

## Non-Goals

- A second Action execution algebra beside `UnitOfWorkOp`.
- Automatic Saga compensation-chain synthesis.
- Embedding compensation handler implementation code in CML.
- Embedding JTA/XA/provider configuration in CML.
- Modeling manual administrator recovery workflow in CML core semantics.
- Rewriting committed transition history.

## References

- `docs/phase/phase-47.md`
- `docs/phase/phase-47.2.md`
- `docs/notes/cml-action-transaction-compensation-proposal.md`
- `asami/goldenport-cncf/docs/phase/phase-64.1.md`
