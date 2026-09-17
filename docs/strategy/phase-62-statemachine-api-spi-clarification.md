# Phase 62 StateMachine API/SPI Strategy Clarification

Status: current clarification
Date: 2026-09-17

This clarification supersedes older Phase 62 strategy wording that describes Action/Participant-level `ORCHESTRATION | CONTINUATION` binding as part of the generated ABI.

The current authority is the serial producer sequence
`PHASE-62 -> PHASE-62.1 -> PHASE-62.2 -> PHASE-62.3` and its matching
documents/checklists. Phase 62 is closed for source/lowering; Phase 62.1 owns the
StateMachine API/SPI and ActionExecution ABI; Phase 62.2 owns generated ABI and
bootstrap; Phase 62.3 owns the fixture, producer evidence and CNCF handoff.

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

The Phase 62 sequence remains intentionally narrow: generate the
StateMachine/Workflow ABI required for the first Skill-driven vertical slice
and hand it to CNCF Phase 77. Only Phase 62.3 closes the complete producer
sequence; it does not claim consumer runtime acceptance.

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
