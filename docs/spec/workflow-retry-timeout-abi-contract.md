# Workflow Retry and Timeout Generated ABI Contract

Status: frozen; implementation in-progress; S66.2 acceptance pending
Phase: [66](../phase/phase-66.md), Step S66.2
Updated: 2026-10-02

## Producer ownership and schema selection

Cozy projects the accepted [normalized source contract](workflow-retry-timeout-source-contract.md)
through both public `modeler-scala` and `modeler-scala-value` routes. One pure
selection applies to the complete ordered `Vector[WorkflowDefinition]`: v2
when any Workflow has nonempty `invocationPolicies`, otherwise v1. Empty input
is v1. The public `schemaVersion` and `bootstrapSchemaVersion` constants retain
their original v1 identities; `policySchemaVersion` and
`policyBootstrapSchemaVersion` identify the policy-bearing v2 products.

For a v2 collection, the shared ABI `VERSION`, every Workflow descriptor's
`schemaVersion`, and the sidecar root use
`cozy.cml.statemachine-workflow-abi.v2`. The bootstrap uses
`cozy.cml.statemachine-workflow-bootstrap.v2`. A mixed collection has one
shared ABI object and one shared bootstrap; all descriptors use v2, including
metadata-free members, whose policy vectors are explicitly empty.

For an entirely metadata-free collection, all generated Scala ABI, Workflow
descriptors, bootstrap and JSON bytes remain the accepted
[Phase62 v1 contract](statemachine-workflow-generated-abi-contract.md).
New declarations, fields, arguments and JSON keys are omitted entirely on
that branch. There are no empty policy defaults appended to v1 output.

## Generated Scala shape

Within the shared `StateMachineWorkflowAbi`, v2 adds only these immutable
policy declarations:

```scala
sealed trait InvocationTarget { def identity: String }
object InvocationTarget {
  final case class Action(identity: String) extends InvocationTarget
  final case class Participant(identity: String) extends InvocationTarget
}
final case class RetryPolicy(maximumAttempts: Int, fixedRetryDelayMillis: Long)
final case class InvocationPolicy(identity: String, target: InvocationTarget,
  actionIdentity: String, retry: Option[RetryPolicy],
  executionTimeoutMillis: Option[Long], source: SourceIdentity)
```

The v2 `WorkflowDescriptor` retains its prior field names, order and types:
`schemaVersion`, `identity`, `version`, `source`, `states`, `actions`,
`requiredOperations`. It appends
`invocationPolicies: Vector[InvocationPolicy] = Vector.empty`. Every generated
Workflow object passes that argument explicitly, in normalized policy order.

Targets retain their declared kind and spelling. `actionIdentity` separately
retains the resolved canonical logical Action. Policy identity and optional
entry `source.line` are preserved. Retry and timeout absence stay `None`.
Attempts are exact decimal Int literals; delays and timeouts are exact decimal
Long literals with `L`, including `9223372036854775807L`. Strings follow the
existing generator's quote, backslash, newline, carriage-return, tab and
ISO-control escaping contract.

All prior state, Action, Operation, Required SPI and source correlations remain
unchanged. ComponentFactory metadata still wraps each descriptor directly;
bootstrap vectors still refer to those metadata values in Workflow order.
The generated declarations perform no invocation, context mutation, timer,
continuation change, retry or timeout enforcement.

## Canonical JSON shape

Compact deterministic JSON retains root fields `schemaVersion`, `workflows`
in that order. Each Workflow retains `identity`, `version`, `source`, `states`,
`actions`, `requiredSpi`, then appends `invocationPolicies`. A metadata-free
member of mixed v2 output has `invocationPolicies: []`.

Each policy has this exact field order and shape:

```json
{"identity":"capture-control","target":{"kind":"ACTION","identity":"capture-payment"},"actionIdentity":"capture-payment","retry":{"maximumAttempts":3,"fixedRetryDelayMillis":250},"executionTimeoutMillis":30000,"source":{"line":123}}
```

The example line is illustrative; production uses the actual normalized entry
line. Target kinds are exactly `ACTION` and `PARTICIPANT`, and target identity
is the declared spelling. Retry is `null` when absent, otherwise an object with
`maximumAttempts` then `fixedRetryDelayMillis`. Timeout and source line are
exact integers or `null`. JSON integers are unquoted base-10 values, including
`9223372036854775807`; there is no floating-point conversion, truncation,
inferred default, target substitution or extra schema/provenance field.

## Real fixture and product paths

The tracked input `src/test/resources/modeler/workflow-invocation-policy.cml`
defines `InvocationControlledWorkflow`, version `workflow-invocation-policy-v2`.
Its `capture-payment` and `check-payment` logical OPERATION Actions each map
through their own REQUIRED-OPERATION capability to
`OrderService.capturePayment`, `PaymentCommand` and `PaymentResult`.
`capture-control` targets the Action with three attempts, 250 ms fixed delay
and 30000 ms timeout; `check-control` targets Participant
`check-payment-capability`, with Long.MaxValue timeout and absent retry.

The executable specification uses these stable output directories:

- `target/test-generated/workflow-invocation-policy-normal`
- `target/test-generated/workflow-invocation-policy-value`

Under each directory, Scala products retain the existing root
`target/scala-3.3.8/src_managed/main/scala/domain/statemachine/workflow/`:
`StateMachineWorkflowAbi.scala`,
`StateMachineWorkflowComponentFactoryBootstrap.scala`, and
`InvocationControlledWorkflowStateMachineWorkflow1.scala`.
The canonical sidecar remains `target/cozy/statemachine-workflow-abi.json`.
No artifact path or object-naming rule changes with schema selection.

`workflow-invocation-policy-legacy.cml` preserves the independent pre-v2
legacy source. `WorkflowInvocationPolicyAbiGenerationSpec` pins its four
original product SHA-256 values through both public routes and covers policy
absence, numeric boundaries, declared aliases, Judgment, escaping, source
correlation, mixed collections, empty input and deterministic repetition.
Its representative normal outputs remain available for the parent-selected
Scala 3.3.8 compilation probe; bounded variants use normalized models without
overwriting those output directories. Validation and independent S66.2 review
remain pending.

## Consumer handoff and exclusions

Cozy owns source normalization and generated producer products. S66.3 owns
CNCF metadata admission and copies actual generated JSON/Scala artifacts as a
CML-agnostic consumer fixture; no consumer fixture is hand-authored here.
CNCF Phase81 owns runtime enforcement. This contract does not add Deadline,
Timer/Wait, Cancellation, FailurePolicy, backoff/jitter, general idempotency,
Iteration, provider selection or new runtime APIs. ADMISSION eligibility
remains the accepted source-validation boundary: policies describe OPERATION
or JUDGMENT invocations, never deterministic local ADMISSION.
