# Phase 62 StateMachine API/SPI Strategy Clarification

Status: current clarification
Date: 2026-09-17

This clarification supersedes older Phase 62 strategy wording that describes Action/Participant-level `ORCHESTRATION | CONTINUATION` binding as part of the generated ABI.

The current Phase 62 authority is `docs/phase/phase-62.md` plus `docs/phase/phase-62-checklist.md`.

## Current strategy

CML/Cozy defines Workflow on the generic StateMachine API/SPI foundation:

```text
StateMachine
  Provided API
  Required SPI
  State / Action / Transition
  ActionExecution
    Completed(Result)
    Suspended(Continuation)
    Failed(Error)
```

Workflow reuses/projects this foundation. There is no Workflow-wide execution protocol mode and no semantic `InvocationBinding = ORCHESTRATION | CONTINUATION` ABI attribute.

The binding that remains is:

```text
StateMachine Required SPI
  -> Provider binding
  -> Provider execution
  -> ActionExecution
```

Participant/capability metadata may describe who or what can satisfy an SPI operation, but it does not determine whether execution suspends. A selected Provider may complete directly, suspend with a durable Continuation, or fail.

## Initial target

Phase 62 remains intentionally narrow: generate the StateMachine/Workflow ABI required for the first Skill-driven vertical slice and hand it to CNCF Phase 77.

```text
BuildProject  -> Completed
RunTests      -> Completed
ReviewChange  -> Suspended(Continuation)
ReviewResult  -> resume
CommitChanges -> Completed
Terminal
```

Workflow-to-Workflow proxy/REST binding, full assemble API/SPI connection, UI Workflow, Flutter generation and offline client synchronization remain follow-up work.

## Historical records

Earlier notes/addenda/journals that describe Orchestration/Continuation binding remain useful as design history when explicitly marked historical/superseded. They are not normative for implementation.
