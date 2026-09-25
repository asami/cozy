# StateMachine Provided API producer ABI

This additive producer is used by CNCF Phase 77.1. It is not a separate Cozy
development Phase and does not change the closed
`cozy.cml.statemachine-workflow-abi.v1` descriptor or bootstrap shape.

## CML declaration

Under a `WORKFLOW` definition, declare at most one `OPERATION`
section. This differs from `OPERATION` under `SERVICE` by its parent context.
Each entry names one existing qualified `SERVICE.OPERATION`; its
heading must match the operation name. The input and result types come from
that normalized CML Service Operation, not from a duplicated type string or an
Action/Required SPI name.

```cml
### OPERATION

#### beginReview

operation = WorkflowService.beginReview
```

Unknown, ambiguous, unqualified, duplicate, or heading-mismatched operations
fail during normalization. No execution mode, provider binding, or transport
is declared here. A Provided operation need not be the same operation as an
Action's Required SPI; the two bindings are explicit and independent.

## Produced metadata

When a Workflow declares at least one Provided operation, both Scala
generation routes emit the versioned
`cozy.cml.statemachine-provided-api-abi.v1` Scala descriptor/bootstrap under
`domain.statemachine.providedapi` and the canonical sidecar
`target/cozy/statemachine-provided-api-abi.json`. The descriptor records the
producer identity, Workflow identity/version/source, and ordered Provided
operation identity, types, and source. A Workflow with no Provided declaration
emits no additional artifact. `StateMachineApiSpi.fromWorkflow` can project
the declared Provided operations without a second caller-supplied list; its
older explicit-list overload remains compatible.

CNCF admits this separate ABI only alongside the matching accepted Workflow
ABI. Dispatch/runtime behavior belongs to CNCF; the producer supplies
declarations only.
