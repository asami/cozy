# Phase 33.1 - CML Action Transaction and Compensation Semantics

Status: planned
Planned at: 2026-09-05
Parent: [Phase 33 - CML Composite StateMachine and Workflow Modeling](phase-33.md)
Cross-repository consumer: `asami/goldenport-cncf` Phase 64.1

## Purpose

Define how CML StateMachine and Composite StateMachine actions express logical
effects whose runtime execution may use a local transaction, a distributed
transaction such as 2PC, or compensation when atomic distributed commit is not
available.

This phase extends the Phase 33 typed Action Program direction without turning
CML into a transaction-manager DSL.

The governing architecture is:

```text
CML StateMachine / Composite StateMachine actions
        |
        v
Typed Action Algebra
        |
        v
Free / inspectable Action Program
        |
        v
Generated effect semantics
        |
        v
CNCF Planner / Interpreter
   +----+-----------------------+
   |                            |
   v                            v
atomic transaction       non-atomic execution
(local / 2PC)             + compensation/recovery
```

## Core Decision

StateMachine action programs are composed before interpretation.

For an atomic execution boundary, all admitted actions and the state transition
succeed or the entire transition aborts.

For effects that cannot participate in the same atomic transaction, CML must
carry enough logical semantics for CNCF to select a reliable execution model.
The model must distinguish at least these cases conceptually:

```text
Atomic
  local transaction

DistributedAtomic
  2PC-capable participant(s)

Compensatable
  forward action + explicit compensation action

Irreversible
  externally visible effect with no semantic inverse
```

The exact CML syntax and final names remain open. These are semantic classes,
not provider-specific transaction APIs.

## Design Principles

- CML expresses logical effect semantics, not JDBC/JTA/XA/provider mechanics.
- A Free Action Program is an execution-independent description, not itself a
  transaction.
- Local atomic actions are interpreted inside the StateMachine UnitOfWork.
- 2PC may be used when all required participants and the configured runtime
  support it; CML must not assume 2PC availability.
- Where atomic distributed commit is unavailable, a forward action may declare
  a compensation action when semantic reversal is possible.
- Compensation is not rollback. It is a new explicit action executed after a
  prior effect has committed.
- Irreversible actions must be modeled as such; the system must not pretend they
  can be rolled back.
- Constituent and composite actions use the same effect model and can coexist in
  one logical composed program.
- Transaction/effect semantics must remain inspectable for validation,
  simulation, review, and generation.

## Proposed Semantic Model

Conceptually:

```text
ActionDefinition
  id
  effectClass
  operation
  compensation?       // for compensatable actions
  idempotency?        // semantic requirement/capability
  sourceLocation
```

Candidate effect classes:

```text
AtomicLocal
DistributedAtomic
Compensatable
Irreversible
```

This is intentionally provisional. A later design may separate transaction
participation capability from reversibility rather than encode both in one
enum. Phase 33.1 must evaluate that alternative explicitly.

A more orthogonal model may be preferable:

```text
ActionEffectSemantics
  transactionCapability:
    local | twoPhaseCapable | none

  reversibility:
    rollback | compensate(actionRef) | irreversible
```

The phase should prefer orthogonal concepts if they avoid invalid combinations
and preserve provider neutrality.

## StateMachine Atomicity

For actions admitted to one atomic boundary:

```text
select transition
  -> construct candidate state
  -> compose local actions
  -> interpret inside UnitOfWork / distributed atomic boundary
  -> persist state
  -> commit all
```

Any pre-commit error aborts the full transition:

```text
action failure
  -> abort
  -> no state commit
  -> no CommittedTransition
```

This invariant is part of StateMachine semantics, not merely an implementation
optimization.

## 2PC Semantics

2PC is an optional runtime realization for a logical atomic segment.

CML should not name XA resources, transaction managers, or provider APIs.
Instead, generated metadata should allow CNCF to verify at admission/planning
whether all actions assigned to a distributed-atomic segment can actually join
one 2PC transaction.

If the model requires distributed atomicity and the runtime cannot provide it,
admission must fail. The runtime must not silently downgrade the semantics to
best-effort after-commit execution.

## Compensation Semantics

When a logical effect cannot participate in the atomic transaction, the model
may define an explicit compensation.

Conceptually:

```text
forward: ReserveInventory
compensate: ReleaseInventory
```

Compensation requirements:

- compensation has its own stable action identity;
- compensation is itself a normal typed Action Program/action definition;
- compensation may fail and therefore needs retry/recovery semantics;
- compensation ordering for multiple completed effects is deterministic,
  normally reverse causal order unless explicitly modeled otherwise;
- compensation must be idempotent or have an explicit deduplication contract;
- the model must not claim exact restoration when compensation is only
  semantically approximate;
- compensating an external effect does not erase the historical fact that the
  forward effect occurred.

## Irreversible Effects

Examples such as sending an email or notifying an external party may have no
meaningful compensation.

Such actions must be explicit in the model/effect analysis. Their placement in a
transition path should generate diagnostics when later failure could leave the
system in a business state requiring recovery or human intervention.

Irreversible does not mean forbidden. It means the model and runtime must expose
the recovery boundary honestly.

## Composite StateMachine Interaction

Actions may be attached to both constituent and derived composite transitions.
Their logical programs are composed in causal order, while transaction planning
is performed afterward.

```text
constituent Action Program
        *>
composite Action Program
        |
        v
logical combined program
        |
        v
effect/transaction planner
```

The planner must not assume the whole combined program is one transaction.
Instead it derives atomic segments and durable boundaries from generated effect
semantics and runtime capability.

## Static Analysis

Cozy/SimpleModeler should detect, where possible:

- compensation references that do not resolve;
- compensation cycles;
- invalid compensation signatures/contracts;
- logically irreversible action placed before a required atomic decision;
- a declared distributed-atomic requirement containing an action that cannot
  support the required capability;
- duplicate constituent/composite effects with conflicting compensation;
- action paths whose failure semantics are underspecified;
- non-idempotent compensation on retryable recovery paths;
- contradictory effect metadata.

Provider capability is runtime-specific and is checked again by CNCF admission.

## Work Stack

| ID | Stage | Outcome | Status |
| --- | --- | --- | --- |
| ACTX-01 | Existing action/transaction inventory | Current CML actions, CNCF UnitOfWork assumptions, external-effect conventions, and any existing compensation metadata are inventoried. | planned |
| ACTX-02 | Effect semantic model | Provider-neutral transaction capability, reversibility, compensation, and irreversibility semantics are frozen. | planned |
| ACTX-03 | Action/compensation grammar | CML syntax for effect semantics and compensation references is defined without exposing runtime transaction APIs. | planned |
| ACTX-04 | Static validation | Compensation graph, capability consistency, idempotency/recovery hazards, and irreversible-boundary diagnostics are implemented. | planned |
| ACTX-05 | Generation | Typed Action Program metadata preserves effect semantics and compensation relations deterministically. | planned |
| ACTX-06 | CNCF handoff | Generated contracts align with CNCF Phase 64.1 planner/interpreter admission and recovery semantics. | planned |
| ACTX-07 | Acceptance | Real CML proves local atomic abort, optional 2PC admission, compensatable external effect, compensation failure/retry, and irreversible-effect diagnostics. | planned |

## Acceptance

- Local actions plus state mutation abort atomically on interpreter failure.
- A model requiring distributed atomicity cannot run when the configured CNCF
  runtime lacks the required 2PC-capable participants.
- A compensatable action carries an explicit typed compensation relation.
- Forward and compensation actions remain distinct historical occurrences.
- Compensation failure is observable and recoverable rather than hidden.
- Irreversible actions are represented honestly and can trigger model/review
  diagnostics.
- Constituent and composite actions share one typed effect model.
- Generated metadata remains provider-neutral.
- CNCF can construct an execution plan without reverse-engineering CML meaning.

## Non-Goals

- Embedding JTA/XA/provider configuration in CML.
- Pretending every external action is rollbackable.
- Automatically synthesizing business compensations from technical inverses.
- A full Saga language independent of StateMachine/Composite StateMachine.
- Hiding compensation or irreversible-effect failures from model review.

## References

- `docs/phase/phase-33.md`
- `docs/notes/cml-action-transaction-compensation-proposal.md`
- `docs/notes/cml-composite-statemachine-workflow-proposal.md`
- `asami/goldenport-cncf/docs/phase/phase-64.1.md`
