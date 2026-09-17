# Generated StateMachine/Workflow ABI Contract

status=accepted
phase=62.2
slice=WFL-62-04
updated_at=2026-09-17

## Scope

This contract defines Cozy's deterministic producer projection for normalized
`WorkflowDefinition` values. It consumes the accepted Phase 62.1 source ABI
and the established CML Workflow source normalization. It defines generated
schemas and bootstrap metadata only; it is not a StateMachine runtime,
provider implementation, or caller integration.

## Fixed artifact identities

Each Scala generation route emits these additive artifacts:

- package `domain.statemachine.workflow`;
- `StateMachineWorkflowAbi.scala`, with schema version
  `cozy.cml.statemachine-workflow-abi.v1`;
- `StateMachineWorkflowComponentFactoryBootstrap.scala`, with bootstrap schema
  version `cozy.cml.statemachine-workflow-bootstrap.v1`;
- one declaration-ordered `<Workflow>StateMachineWorkflow<N>.scala` descriptor
  for each normalized Workflow; and
- canonical JSON at `target/cozy/statemachine-workflow-abi.json`.

These paths and schema versions are independent of, and additive to, every
existing Composite StateMachine v1 artifact. Direct `COMPOSITE-STATEMACHINE`
input retains its existing output unchanged.

## Generated schema boundary

`StateMachineWorkflowAbi` contains the complete immutable generic Phase 62.1
schema: typed operation, input-type, result-type, Required SPI, run,
continuation, and revision identities; `StateMachineApiSpi`;
`StateMachineProvidedOperation`; `StateMachineRequiredOperation`; the
metadata, context, completion, evidence, input, result, failure, provider
request, and continuation shapes; `StateMachineProviderBinding`; and the
closed `ActionExecution` algebra. `StateMachineRequiredOperation.metadata` is
mandatory and `ContinuationResult` retains the complete typed issued metadata,
completion, completion-fact, and evidence fields. These generic types are
schema declarations only: this producer does not generate runtime validation
or a Provider implementation.

`ActionExecution` has exactly the schema alternatives
`Completed(StateMachineOperationResult)`,
`Suspended(Continuation)`, and `Failed(StateMachineOperationFailure)`.
`StateMachineProviderBinding` is exactly a schema relationship from a
`StateMachineRequiredOperationIdentity` to a `ProviderIdentity`. No generated
Workflow descriptor selects a provider or creates a binding value.

The generated Workflow descriptor carries only source-model data. Its
declaration-ordered `WorkflowRequiredOperationDescriptor` values are separate
from the generic `StateMachineRequiredOperation` schema and carry only typed
Required SPI identity, Action identity, operation identity, input and result
type references, capability-entry source, and Action source. The Workflow
descriptor otherwise retains:

- Workflow identity and opaque source version;
- separate `WORKFLOW` root and structural-definition source locations;
- ordered State names and sources;
- ordered Action identities, kinds, input bindings, and sources;
- Operation service/name/input/result type references and the Action source
  that declares each Operation.

No CML source in this phase supplies context, completion, evidence, constraint,
or provider-binding values. The source-only descriptor consequently never
constructs a generic Required SPI value or metadata: Phase 62.1's
`StateMachineApiSpi.fromWorkflow` caller-supplied resolver remains the only
authority that can supply mandatory generic Required SPI metadata.

The source model has no independent Operation location. Consequently, the
generated Operation source is exactly the owning logical Action source; this
is correlation, not a newly inferred location.

## Direct ComponentFactory bootstrap metadata

Each per-Workflow descriptor object exposes both `workflow` and
`componentFactoryMetadata`. The latter is a typed metadata wrapper with a
direct Scala reference to that object's `workflow` value.

`StateMachineWorkflowComponentFactoryBootstrap.componentFactoryMetadata` is a
declaration-ordered `Vector` of those per-Workflow metadata values. It is
constructed from direct Scala references and performs neither inferred name
lookup nor factory instantiation. It supplies no implementation, lifecycle,
or execution policy.

## Canonical JSON and compatibility

The JSON sidecar has fields in this exact order: `schemaVersion`, then
`workflows`. Workflow records follow normalized declaration order. Within each
Workflow record the field order is `identity`, `version`, `source`, `states`,
`actions`, and `requiredSpi`; nested records likewise preserve their declared
field and vector order. JSON is a canonical sidecar of source-model data and
does not represent direct Scala references.

For this v1 contract, all emitted names, paths, field ordering, and schema
versions are stable. Additive fields or new schema versions require a new ABI
version; existing Composite StateMachine v1 artifacts are not rewritten by
this contract.

## Exclusions

This producer does not emit provider selection, execution modes, runtime
policy, inferred name matching, persistence, transport, REST, UI, proxy,
Skill-specific behavior, raw shell behavior, queues, schedulers, or external
caller integration. It does not add CML grammar or `InvocationBinding`.

Phase 62.3 owns fixture and consumer-facing validation; this contract does not
implement that successor work.
