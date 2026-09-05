# CML Actions to Existing UnitOfWork Algebra

Date: 2026-09-05
Status: design decision

## Context

Phase 33.2 initially proposed a new typed `ActionOp` hierarchy for CML
StateMachine, Composite StateMachine, and Workflow actions.

A review of `goldenport-cncf` found that CNCF already has the canonical Free ×
UnitOfWork execution model:

```text
UnitOfWorkOp[A]
ExecProgram[A] = Program[UnitOfWorkOp, A]
ExecUowM[A]    = UowM[UnitOfWorkOp, A]
```

`UnitOfWorkOp` is explicitly the single source of truth for executable intents.
Creating a second StateMachine/Workflow execution algebra would duplicate an
existing architectural boundary and risk divergence.

## Decision

CML will keep logical actions as model elements, but their executable form will
compile/resolve into the existing CNCF `ExecProgram` / `UnitOfWorkOp` model.

```text
CML Logical Action
  -> Action Resolver / Compiler
  -> ExecProgram[UnitOfWorkOp]
  -> CNCF UnitOfWork analysis / interpreter
```

There is no canonical parallel `StateMachineActionOp` or `WorkflowActionOp`
algebra.

## Consequences

- StateMachine, Composite StateMachine, Workflow, ordinary CNCF Actions, and
  direct/declarative execution converge on one executable-intent language.
- Constituent and composite transition actions compose as existing Free/UoW
  programs.
- CML preserves logical identity, typing, transaction requirements,
  reversibility, compensation, idempotency, ordering, and provenance as
  model/generated metadata.
- CNCF remains responsible for interpreting/planning the resulting program.
- `ExecProgram` is not assumed to be one datastore transaction because existing
  `UnitOfWorkOp` already includes both local and external effects.
- CNCF Phase 64.2 must classify/analyze the existing operation algebra for local
  atomic, 2PC, after-commit compensatable, and irreversible execution.
- A new `UnitOfWorkOp` primitive is introduced only when a missing intent is
  generic CNCF functionality, not merely because StateMachine/Workflow needs a
  new model concept.

## Testability

The decision strengthens the previous testability direction. StateMachine and
Workflow tests can use the same Free structure and interpreter/fake-driver
boundaries already intended by the CNCF execution model.

The canonical test path becomes:

```text
CML
 -> pure transition/composite evaluation
 -> compiled ExecProgram
 -> test UnitOfWork interpreter / fake drivers
```

This avoids production I/O while preserving the same executable-intent
structure used in production.

## Updated document

- `docs/phase/phase-33.2.md`
