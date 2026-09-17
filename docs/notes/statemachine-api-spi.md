# StateMachine API/SPI

## Core

StateMachine is the foundational API/SPI-bearing executable model. Workflow reuses this mechanism rather than defining a parallel Workflow-specific interface system.

```text
StateMachine
  Provided API
  Required SPI
  State / Action / Transition
  ActionExecution = Completed | Suspended | Failed
```

## Provided API

Typed external operations that start, query or otherwise interact with the StateMachine according to its public contract.

## Required SPI

Typed operations/capabilities required by StateMachine Actions but supplied by providers outside the StateMachine implementation boundary.

A required operation preserves:

- stable identity
- typed input/result
- generic Context requirements
- Completion/Evidence contract
- required capabilities/constraints where meaningful

## Provider binding

```text
StateMachine SPI
  -> LocalProvider
  -> ExternalContinuationProvider
  -> TestProvider
```

Provider placement is independent of StateMachine semantics. External provider execution may produce `Suspended(Continuation)`; local/test providers may produce `Completed(Result)`.

## Workflow reuse

Workflow is a process-oriented projection/specialization around StateMachine semantics. Workflow API/SPI views are projections of the underlying StateMachine API/SPI plus Workflow-specific descriptive metadata such as purpose, actors and related use cases.

## Assemble

CML assemble should eventually support:

- binding StateMachine SPI to Component API or another StateMachine/Workflow API;
- exposing selected StateMachine API through the owning Component API;
- transport-neutral logical binding, leaving local/REST placement to runtime/deployment configuration.

This common mechanism should also remain reusable for UI StateMachines, Job StateMachines and Entity lifecycle StateMachines.
