# Workflow Connection / UI Workflow Generation

## Long-term model

CML Workflow should eventually model a typed connection from a Business Workflow Action to a Participant Workflow executed in another runtime, including a client UI runtime.

```text
Business Workflow Required SPI
  -> Provider binding
  -> Participant Workflow Provider
  -> ActionExecution.Suspended(Continuation)
  -> typed Result
  -> Business Workflow resume
```

The connection is semantic, not a UI command. A server workflow does not model `openDialog`; it models operations such as `RequestApproval : ApprovalContext -> ApprovalDecision`.

A Participant/UI Workflow may internally contain multiple local states, screens, actions, device operations, local AI operations and human interactions while remaining one semantic participant invocation from the Business Workflow perspective.

## Future generated artifacts

Potential Cozy outputs:

- server Workflow ABI for CNCF
- Workflow Connection/Binding ABI
- UI Workflow IR
- generated Flutter/Dart UI Workflow
- Continuation client/server adapters

Potential UI Workflow IR concepts:

```text
UiWorkflowIr
  states
  transitions
  actions
  screens
  forms
  localOperations
  continuationInput
  continuationResult
```

## Scope boundary

This is a roadmap, not the initial Workflow implementation target.

The initial target is reliable Skill-driven Workflow execution using generic StateMachine Required SPI, Provider binding, and `ActionExecution` / Continuation contracts. Phase 62 should establish only the generic semantic/ABI foundations needed by that target and avoid implementing UI Workflow grammar or Flutter generation prematurely.

The initial ABI should remain extensible enough to bind a Required SPI to a Participant Workflow Provider later without changing the core Operation input/result semantics. A future direct provider may instead return `Completed(Result)`; Participant identity does not prescribe either outcome.
