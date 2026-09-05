# CML Action Transaction and Compensation Proposal

Status: draft / non-normative
Date: 2026-09-05
Target: Cozy Phase 47.1 / CNCF Phase 64.1

## Purpose

Refine the typed Action Program model for StateMachine and Composite
StateMachine so logical actions can be interpreted under different execution
models without embedding provider transaction details in CML.

## Principle

A StateMachine action is first modeled as a typed logical operation and then
composed into an inspectable Action Program. Runtime execution is delegated to a
planner/interpreter.

```text
CML Action
  -> Typed ActionOp
  -> Free / inspectable Action Program
  -> Execution Planner
  -> Interpreter
```

The planner determines which parts of the program can be atomic and which need
post-commit, distributed, compensation, or irreversible handling.

## Atomic Transition Rule

When all actions required for a transition participate in one admitted atomic
boundary, action execution and state mutation are one unit:

```text
transition selection
  -> candidate state
  -> composed actions
  -> interpretation
  -> persist state
  -> commit
```

Any error before commit aborts the transition. No state mutation and no
`CommittedTransition` become authoritative.

## Transaction Capability and Reversibility

Transaction capability and reversibility are related but not identical. The
preferred semantic model is therefore orthogonal rather than one overloaded
classification.

Conceptually:

```text
ActionExecutionSemantics
  transactionCapability
  reversibility
```

Transaction capability candidates:

```text
LocalAtomic
TwoPhaseCapable
NonTransactional
```

Reversibility candidates:

```text
RollbackByTransaction
Compensatable(compensationActionRef)
Irreversible
```

The exact names remain open. The important rule is that CML expresses semantic
requirements/capabilities without exposing JTA/XA/provider details.

## 2PC

If an execution segment requires multiple resources to commit atomically and
all participants are 2PC-capable, CNCF may interpret it using two-phase commit.

```text
Action Program segment
  -> prepare participant A
  -> prepare participant B
  -> commit all / abort all
```

CML states the atomicity requirement and action semantics. CNCF owns resource
capability discovery and transaction-manager integration.

A required distributed-atomic segment must fail admission when the runtime
cannot provide the required guarantee. Silent downgrade to asynchronous or
best-effort execution is invalid.

## Compensation

When an external action cannot participate in the same atomic transaction, CML
may associate it with an explicit compensation action.

```text
ReserveInventory
  compensate -> ReleaseInventory
```

Compensation is a new forward action, not a database rollback.

Required semantics:

- stable forward-action identity;
- stable compensation-action identity;
- deterministic relation between them;
- compensation executed only for a forward occurrence that actually completed;
- compensation occurrence recorded separately;
- idempotency/deduplication contract for retry;
- compensation failure is observable and recoverable;
- reverse causal ordering is the default candidate for multiple completed
  compensatable effects;
- business meaning may be approximate and should not be described as exact
  rollback unless it truly is.

## Irreversible Effects

Some externally visible effects have no useful inverse.

Examples may include:

- email already delivered;
- notification already observed;
- external physical action;
- publication to a non-transactional audience.

These actions are not forbidden. They are explicitly irreversible so model and
runtime review can reason about failure after the effect occurs.

## Composite StateMachine

Lower-level and composite-level actions remain independent logical programs and
compose in deterministic causal order.

```text
constituentProgram
  *>
compositeProgram
  -> combined logical program
  -> effect planner
```

The effect planner may split the combined program into multiple execution
segments. Composition does not imply one physical transaction.

## Action Plan

A possible generated/runtime planning model is:

```text
ActionExecutionPlan
  atomicSegments[]
  afterCommitSegments[]
  compensationEdges[]
  irreversibleBoundaries[]
```

This is a CNCF runtime representation, not necessarily a CML surface type.

## Static Validation

The CML/Cozy side should reject or warn on:

- unresolved compensation action;
- compensation cycles;
- compensation incompatible with the forward action contract;
- impossible declared atomicity;
- contradictory transaction/reversibility metadata;
- non-idempotent compensation on a retryable path without explicit handling;
- duplicate logical effects with different compensation definitions;
- irreversible action placed before a later modeled step that requires strong
  rollback-like semantics;
- compensation declared for a forward action that can never complete before
  failure.

Runtime provider capability remains a CNCF admission concern.

## Generated Contract

SimpleModeler should preserve typed action semantics such as:

```text
ActionDefinition
  id
  operation
  transactionSemantics
  reversibilitySemantics
  compensationRef?
  idempotencySemantics?
  sourceLocation
```

The generated Free-program representation must retain action identity and source
provenance so planners, interpreters, diagnostics, simulation, and review can
reason about it.

## Open Questions

1. Whether atomicity requirement belongs on individual ActionOps, explicit
   action groups/segments, or both.
2. How nested/composite action programs establish transaction segment
   boundaries.
3. Exact CML syntax for compensation references.
4. Whether idempotency is a required semantic property, a capability, or both.
5. Compensation ordering customization beyond reverse causal order.
6. Handling of partial compensation and manual recovery.
7. Whether compensation itself may have a compensation or must terminate the
   compensation chain.
8. Interaction with existing/future Saga concepts in CNCF.
9. Which checks belong to Cozy static analysis versus CBD Support design review.

## Governing Rules

> Compose logical actions first; choose execution mechanics later through the
> CNCF planner/interpreter.

> If all required effects participate in an admitted atomic boundary, failure
> aborts the StateMachine transition as a whole.

> If atomic commit cannot be guaranteed, use explicit compensation where
> possible and explicit irreversible/recovery semantics where it is not.
