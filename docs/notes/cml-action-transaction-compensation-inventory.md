# CML Action Transaction and Compensation Current Inventory

Status: non-normative factual inventory

Date: 2026-09-08

Scope: Cozy Phase 47.1 ACTX-01. This note records the current Cozy producer
surface and its explicit deferrals. It does not add CML syntax, transaction
execution, compensation execution, recovery behavior, or an external-repository
fact. Future work is labeled separately from current evidence.

## Evidence boundary

The current implementation evidence is limited to these Cozy sources and
accepted design documents:

- [`CompositeStateMachineCml.scala`](../../src/main/scala/cozy/modeler/CompositeStateMachineCml.scala), especially `_actions`;
- [`CompositeStateMachineDefinition.scala`](../../src/main/scala/cozy/modeler/CompositeStateMachineDefinition.scala), especially the normalized logical-action and occurrence case classes;
- [`cml-composite-statemachine-generation.md`](../design/cml-composite-statemachine-generation.md), the accepted CSM-07 generation authority;
- [`cml-composite-statemachine-action-algebra.md`](../design/cml-composite-statemachine-action-algebra.md), the accepted CSM-04 pure action-algebra authority;
- [`ModelServiceOperationProjector.scala`](../../src/main/scala/cozy/modeler/ModelServiceOperationProjector.scala) and [`ModelRuntimeDefinitionProjector.scala`](../../src/main/scala/cozy/modeler/ModelRuntimeDefinitionProjector.scala), for current generated operation projection placeholders; and
- the [Phase 47.1 contract](../phase/phase-47.1.md), which defines the future producer-handoff scope.

The 2026-09-05 [transaction and compensation proposal](cml-action-transaction-compensation-proposal.md)
is explicitly draft/non-normative. Its exploratory reversibility enum is not
the selected v1 Phase contract and is not evidence of current implementation.

## Current Cozy facts

### CML Action syntax and validation

`CompositeStateMachineCml._actions` reads each declared Action's identity,
`KIND`, `OPERATION`, and optional `INPUT` at
[`CompositeStateMachineCml.scala`](../../src/main/scala/cozy/modeler/CompositeStateMachineCml.scala).
The current implementation accepts only `KIND = OPERATION`; the same method
rejects raw expression, script, provider, transaction, retry, and compensation
syntax. An operation must resolve to one normalized CML operation, and the
optional input binding must match the operation input type and a typed
constituent subject. This is the current parser/model boundary, not a future
transaction metadata implementation.

### Logical action fields and occurrence provenance

`CompositeStateMachineDefinition.scala` defines the normalized logical Action
with identity, kind, resolved operation, optional input binding, and source
identity. The operation contains service, name, and optional input type. The
definition also retains action occurrences with their identity, role or
derived-transition placement, from/to/on information, the logical Action, and
source identity. These vectors preserve declaration and causal placement
provenance; they do not contain transaction, compensation, retry, or recovery
fields.

### CSM-07 generation

The accepted CSM-07 authority states that validation and normalized projection
produce the typed `CompositeStateMachineDefinition` ABI, including the logical
Action and action-occurrence data above. Its explicit deferrals include
transaction, retry, compensation, recovery, persistence, concurrency, and
lifecycle policy. Therefore current generation serializes the existing logical
Action and occurrence data and explicitly defers transaction/compensation/
recovery policy. No current generated producer contract should be described as
carrying the Phase 47.1 v1 metadata until later ACTX work implements it.

### CSM-04 ActionProgram

The accepted CSM-04 action-algebra authority defines `ActionProgram` as an
inspectable ordered free-program representation. Its composition preserves
causal ordering and occurrence provenance. It explicitly says that the
ActionProgram is not physical execution, a transaction, or a claim about effect
timing, and defers failure, retry, compensation, and recovery. CSM-04 is thus
pure causal ordering, not execution or transaction policy.

### Current generated operation projections

The current operation projection paths contain `UnitOfWorkOp` placeholders, not
a completed CML Action runtime path. For example, the command/query fallback
bodies in `ModelServiceOperationProjector` use
`uowmNotImplemented[org.goldenport.cncf.unitofwork.UnitOfWorkOp, Unit]`, and
the create/save/update/delete fallback branches in
`ModelRuntimeDefinitionProjector` use the same placeholder family. These are
current generated-operation projection seams; they are not evidence that CML
Actions execute through UnitOfWork, and they do not prove rollback, 2PC,
compensation, or durable recovery behavior.

## Future work, not current evidence

Phase 47.1 v1 requires future Cozy producer metadata, static validation, and
deterministic generation for action effect class, logical transaction
requirement, idempotency, optional compensation-handler reference, and
ordering/provenance. The exact CML syntax and concrete metadata/ABI field
representation remain later ACTX decisions. This inventory does not claim that
the future fields or validations exist now.

The Phase 47.1 producer handoff carries those future producer-contract
requirements to the consumer boundary. UnitOfWork execution, optional-2PC
capability/admission behavior, compensation execution, durable
`RecoveryRequired`, and runtime proof are explicitly consumer-owned by
`goldenport-cncf` Phase 64.1 under the Phase contract. This statement records
the selected ownership boundary; it does not state an observed external
repository revision or claim that external runtime work has started or
completed.

## Non-claims

This factual inventory does not claim:

- that CML currently has transaction, retry, compensation, Saga, or recovery
  syntax;
- that current Cozy generation contains the Phase 47.1 v1 metadata;
- that `UnitOfWorkOp` placeholders constitute a completed Action runtime path;
- that Cozy executes or proves local rollback, optional 2PC admission,
  compensation success/failure, or durable `RecoveryRequired`; or
- that any external repository has changed or accepted a runtime contract.
