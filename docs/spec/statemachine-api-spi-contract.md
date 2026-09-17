# StateMachine API/SPI Source Contract

status=accepted
phase=62.1
slice=WFL-62-02-03A
updated_at=2026-09-17

## Scope

This specification defines the Cozy source ABI for one reusable StateMachine
Provided API / Required SPI contract and the durable Provider outcome boundary.
It consumes the normalized CML StateMachine and Workflow model. It does not
define generated code, a runtime, or a new CML grammar.

## Stable operation and type references

`StateMachineOperationIdentity` is the stable `service.operation` identity of
one logical operation. `StateMachineInputTypeReference` and
`StateMachineResultTypeReference` preserve the declared input and result type
references without interpreting their payloads.

`StateMachineProvidedOperation` is an explicit caller-supplied descriptor. A
Provided API is not inferred from a Workflow, CML extension, or generator
output in this phase.

## Workflow Required SPI projection

`StateMachineApiSpi.fromWorkflow(workflow, providedOperations,
requiredOperationMetadata)` is the sole
Workflow-specific projection adapter. It converts each existing normalized
`WORKFLOW REQUIRED-OPERATION` mapping into a `StateMachineRequiredOperation`.
The caller-supplied resolver maps each existing Workflow mapping to generic
metadata; the projection retains it in the reusable descriptor. The projected
descriptor therefore independently retains:

- its Required SPI capability identity;
- the existing logical `OPERATION` Action identity;
- the stable service.operation identity; and
- the input and output/result type references;
- `ContextContract` required facts and references;
- `CompletionContract` required facts;
- `EvidenceContract` required evidence; and
- typed `StateMachineConstraint` values.

The reusable StateMachine API/SPI model has no other dependency on `Workflow`.
It does not create a parallel Workflow interface model.

Service operation normalization retains direct `INPUT`, `OUTPUT`, and `RESULT`
values and nested `INPUT TYPE`, `OUTPUT TYPE`, and `RESULT TYPE` declarations.
`CompositeStateMachineOperation.outputType` is additive and defaults to `None`,
so existing three-argument source construction remains compatible.

## Provider boundary

`StateMachineProviderBinding` contains only a Required SPI identity and a
`ProviderIdentity`. It has no invocation mode, orchestration choice,
Participant, Action placement, or Workflow metadata.

`ProviderExecutionRequest` carries the selected Required SPI descriptor, typed
optional input reference, and current context bundle. Completion and evidence
requirements are reached only through `requiredOperation.metadata`, so a
provider request cannot carry a mutable duplicate of its issued contract. A
`StateMachineProvider` returns exactly one `ActionExecution`:

- `Completed(StateMachineOperationResult)`;
- `Suspended(Continuation)`; or
- `Failed(StateMachineOperationFailure)`.

The provider, rather than a Workflow-wide mode or binding field, determines
which outcome applies.

## Durable continuation and fail-closed resume

`Continuation` carries the StateMachine run identity, continuation identity,
expected revision, full Required SPI descriptor, and `ContextBundle`. The
descriptor preserves operation and Required SPI identities together with
context, completion, and evidence contracts, avoiding mutable duplicate
continuation fields. `ContextBundle` includes a `ContextSnapshot`; its
references preserve versioned external context rather than copying a complete
runtime context into the continuation.

`ContinuationResult` carries the returned run identity, continuation identity,
expected revision, operation and Required SPI identities, context snapshot,
the complete typed issued `StateMachineRequiredOperationMetadata` snapshot,
typed completion result, completion facts, and evidence. The result metadata
preserves `ContextContract`, `CompletionContract`, `EvidenceContract`, and
`StateMachineConstraint` values as one descriptor boundary rather than
independently duplicating selected contracts. `ContinuationResumeValidator` is
pure. It accepts a result only when all of the following match or cover the
issued continuation:

- run identity;
- continuation identity;
- expected revision; and
- context snapshot;
- Required SPI identity and operation identity;
- the declared result type;
- the complete context contract and constraint vector;
- all issued context-contract facts and references;
- completion and evidence contracts; and
- every required completion fact and evidence reference.

Each mismatch is fail-closed through the sealed
`ContinuationResumeRejection` algebra. Rejection returns an observable value;
it neither advances StateMachine state nor rebinds context or stale evidence.

## Exclusions and successors

This source ABI does not add generated language APIs, client SDKs, REST
proxies, provider registries, persistence, queues, schedulers, Skill runtime,
UI, remote invocation, or a CML Workflow grammar for execution placement. It
does not reintroduce `InvocationBinding`, `ORCHESTRATION`, or `CONTINUATION`
as Action, Participant, or Workflow metadata.

Phase 62.2 owns generated-contract implementation. Phase 62.3 owns
integration, aggregate validation, and producer handoff. Existing Composite
StateMachine v1 generators and Bootstrap behavior remain unchanged.
