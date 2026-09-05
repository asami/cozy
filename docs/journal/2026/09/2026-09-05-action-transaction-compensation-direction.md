# Action Transaction and Compensation Direction

Date: 2026-09-05
Status: design decision record

## Context

Composite StateMachine design established that constituent and composite
transition actions can coexist and should be composed into one typed,
inspectable Action Program before runtime interpretation.

The next question was what happens when actions involve external I/O.

For local effects, the preferred invariant is straightforward: the composed
StateMachine action program and state mutation execute in one UnitOfWork; any
pre-commit error aborts the entire transition.

External effects make the atomicity boundary dependent on runtime transaction
capability.

## Decision

CML action semantics will distinguish logical effect requirements from runtime
transaction mechanics.

The generated Action Program remains pure/inspectable. CNCF decides how to
execute the program through a planner/interpreter.

Three execution situations must be represented honestly:

1. local atomic execution;
2. distributed atomic execution when 2PC is available and required; and
3. non-atomic external execution with explicit compensation or explicit
   irreversible/recovery semantics.

## 2PC

Two-phase commit is an optional runtime mechanism, not a CML assumption.

If the model requires a distributed atomic segment and all participants support
2PC, CNCF may execute it as one distributed transaction.

If the runtime cannot provide the declared guarantee, the model must fail
admission. It must not silently downgrade atomic semantics to best effort.

## Compensation

When an external effect cannot join the atomic transaction, compensation is the
preferred model when a meaningful business inverse exists.

```text
ReserveInventory
  -> compensate with ReleaseInventory
```

Compensation is not rollback. The forward action already happened and remains
part of history. Compensation is a new action occurrence that attempts to
restore an acceptable business condition.

Compensation itself can fail and therefore needs retry, idempotency,
observability, and recovery semantics.

## Irreversible effects

Some actions cannot be compensated meaningfully. They must be marked/modelled
as irreversible rather than pretending the StateMachine can roll them back.

This distinction allows model review to identify paths where later failure may
require manual/business recovery.

## Orthogonal semantics

Transaction participation and reversibility should not be collapsed too early
into one enum.

A promising model separates:

```text
transaction capability
  local atomic | 2PC-capable | non-transactional

reversibility
  transaction rollback | compensatable | irreversible
```

This will be tested during Phase 47.1.

## Relationship to Composite StateMachine

Constituent and composite action programs compose first in causal order. The
resulting logical program is then partitioned by the runtime planner into
atomic/distributed/after-commit/compensation segments.

Thus composition of model semantics does not imply one physical transaction.

## Follow-up

Cozy:

- Phase 47.1 defines CML action transaction/reversibility semantics and static
  validation.
- `docs/notes/cml-action-transaction-compensation-proposal.md` is the active
  design note.

CNCF:

- Phase 64.1 defines planner/interpreter, 2PC admission, compensation execution,
  irreversible-effect handling, and recovery.

This keeps the boundary consistent with the existing principle:

> CML defines what the action means; CNCF defines how the admitted action is
> executed reliably.
