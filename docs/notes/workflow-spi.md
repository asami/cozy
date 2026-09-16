# Workflow SPI

## Definition

A Workflow/StateMachine may require typed operations whose providers live outside the Workflow runtime boundary. These required operations form the **Workflow SPI**.

Workflow core semantics remain:

```text
State -> Action -> ActionExecution -> Result/Transition
```

`ActionExecution` is modeled as:

```text
Completed(Result)
Suspended(Continuation)
Failed(Error)
```

Continuation is therefore not a Workflow execution mode. It is the durable suspension value produced by an Action implementation when completion requires an external SPI provider/result.

## External specification

Workflow SPI can be projected from typed required Actions:

```text
RequiredOperation
  identity
  inputType
  resultType
  ContextContract
  CompletionContract
  EvidenceContract
  requiredCapabilities
```

Generated artifacts may include interface specifications, JSON Schema, provider compatibility metadata, test-provider stubs and adapter inputs.

## Binding

A required operation may be bound to:

- an in-runtime/direct provider, producing `Completed(Result)`;
- an external provider, producing `Suspended(Continuation)` until `resume(Result)`;
- a test/mock provider for executable Workflow specifications.

The Workflow definition does not change merely because provider placement changes.

## Future Workflow connection

A Participant/UI Workflow can implement a Workflow SPI operation as a Provided Interface. Future CML Workflow Connection modeling should type-check Required/Provided input and result contracts rather than coupling internal state machines.
