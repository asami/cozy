# Workflow Scheduling and Lifecycle Generated ABI Contract

Semantic role: Stable Semantics (normative)
Current status: S67.2 producer accepted by local Step commit 43af3f1cfe3fd37ae922cfe984b94c8fd6147452; P67-S672-VAL-007 passed 195 tests, P67-S672-VAL-009 compiled ten Scala 3.3.8 sources, and independent review P67-S672-REVIEW-001 passed
Owner: Cozy Workflow generated producer ABI, Phase 67 parent
Update rule: update with the paired executable specification when accepted producer semantics change; acceptance status requires parent evidence.
Phase: [67](../phase/phase-67.md), S67.2; [source contract](workflow-scheduling-lifecycle-source-contract.md), S67.1
Updated: 2026-10-02

## Producer ownership and version selection

Cozy projects accepted immutable `WorkflowDefinition` values through both public
`modeler-scala` and `modeler-scala-value` routes. The
[source contract](workflow-scheduling-lifecycle-source-contract.md) admits a
direct `LIFECYCLE` with optional deadline and exact `COOPERATIVE` cancellation,
and ordered `WAIT` entries with a resolved CSM State and exactly one Timer or
Signal trigger. Deadline is relative to Workflow start; Timer delay is relative
to each entry into the target State. Source deadlines range from 1 through
`Long.MaxValue`, and delays from 0 through `Long.MaxValue`. Signal identity is
opaque. Omitted source values remain absent in the producer.

One version selection covers the entire ordered Workflow collection:

| Collection condition, in priority order | JSON/shared ABI/member schema | Bootstrap schema |
| --- | --- | --- |
| Any `lifecyclePolicy.nonEmpty` or `waits.nonEmpty` | `cozy.cml.statemachine-workflow-abi.v3` | `cozy.cml.statemachine-workflow-bootstrap.v3` |
| Otherwise any nonempty `invocationPolicies` | `cozy.cml.statemachine-workflow-abi.v2` | `cozy.cml.statemachine-workflow-bootstrap.v2` |
| Otherwise, including empty input | `cozy.cml.statemachine-workflow-abi.v1` | `cozy.cml.statemachine-workflow-bootstrap.v1` |

`lifecycleSchemaVersion` and `lifecycleBootstrapSchemaVersion` identify v3.
The established v1 `schemaVersion`/`bootstrapSchemaVersion` and v2
`policySchemaVersion`/`policyBootstrapSchemaVersion` retain their values.
Every v3 member, including legacy and invocation-only members in a mixed
collection, uses v3. The shared ABI `VERSION`, JSON root and member descriptors
agree. Bootstrap metadata references members in collection order.

Without lifecycle policy or waits, the accepted
[v1](statemachine-workflow-generated-abi-contract.md) and
[v2](workflow-retry-timeout-abi-contract.md) products retain their exact bytes.
Neither old branch emits lifecycle declarations, arguments or JSON keys.
Empty input retains the v1 ABI, empty workflows array and empty bootstrap.

## Generated Scala contract

V3 retains every existing invocation, State/Action/SPI/source, context,
provider, execution and continuation declaration and adds only these immutable
types inside `StateMachineWorkflowAbi`:

```scala
sealed trait CancellationMode { def canonicalValue: String }
object CancellationMode {
  case object Cooperative extends CancellationMode {
    val canonicalValue: String = "COOPERATIVE"
  }
}
final case class LifecyclePolicy(deadlineMillis: Option[Long], cancellation: Option[CancellationMode], source: SourceIdentity)
sealed trait WaitTrigger
object WaitTrigger {
  final case class Timer(delayMillis: Long) extends WaitTrigger
  final case class Signal(identity: String) extends WaitTrigger
}
final case class WaitDeclaration(identity: String, state: String, stateIdentity: String, trigger: WaitTrigger, source: SourceIdentity)
```

`WorkflowDescriptor` preserves the original seven fields and appends, in order:

```scala
invocationPolicies: Vector[InvocationPolicy] = Vector.empty,
lifecyclePolicy: Option[LifecyclePolicy] = None,
waits: Vector[WaitDeclaration] = Vector.empty
```

These trailing defaults retain source compatibility for existing seven-argument
v1 and eight-argument v2 Scala constructor clients when compiled against v3.
This is a source-constructor guarantee, not a binary compatibility claim.
V3 always declares invocation types and explicitly supplies each member's
invocation vector, even for wait-only or lifecycle-only collections.

Generated lifecycle/wait arguments preserve declaration order, identity and
source location. `state` retains declared spelling, including aliases;
`stateIdentity` retains the canonical CSM State name separately. Optional
values are `None` when absent; absent policy is `None` and absent waits are
`Vector.empty`. Numeric values are exact decimal Long literals suffixed `L`,
including `0L` and `9223372036854775807L`. Cancellation uses the closed
`CancellationMode.Cooperative` value without invented modes or defaults.
Strings use established quote, backslash, newline, carriage-return, tab and
ISO-control escaping consistently with all other producer fields.

## Canonical JSON contract

Root fields remain `schemaVersion`, then `workflows`. Every v3 Workflow has
`identity`, `version`, `source`, `states`, `actions`, `requiredSpi`, followed by
`invocationPolicies`, `lifecyclePolicy`, `waits`, in exactly that order.
Invocation semantics retain the v2 contract; an absent invocation vector is
`[]`. An absent lifecycle policy is `null`, and absent waits are `[]`.

A lifecycle object has fields `deadlineMillis`, `cancellation`, `source`, in
that order. Deadline is an unquoted exact decimal integer or `null`.
Cancellation is exactly `"COOPERATIVE"` or `null`. Source is `{"line":N}`,
with the actual normalized location or `{"line":null}` for a sourceless IR.

Each wait has fields `identity`, `state`, `stateIdentity`, `trigger`, `source`,
in that order. Timer trigger fields are `kind`, `delayMillis`, with kind
`"TIMER"` and an exact numeric delay. Signal trigger fields are `kind`,
`identity`, with kind `"SIGNAL"` and the exact opaque signal string.
All arrays retain normalized declaration order. Neither representation uses
Double conversion, coercion, manufactured defaults or inferred source lines.
JSON Long maxima remain `9223372036854775807`, without exponent notation or
precision loss; escaping preserves decoded strings exactly.

## Products, specifications and consumer boundary

The tracked input is
[`workflow-lifecycle-policy.cml`](../../src/test/resources/modeler/workflow-lifecycle-policy.cml),
with `LifecycleWorkflow`, version `workflow-lifecycle-v3`, the deadline and
cooperative cancellation, a `payment-timer` Timer and `completion-signal`
Signal. Both routes retain the existing paths beneath their save directory:

- `target/cozy/statemachine-workflow-abi.json`
- `target/scala-3.3.8/src_managed/main/scala/domain/statemachine/workflow/StateMachineWorkflowAbi.scala`
- `target/scala-3.3.8/src_managed/main/scala/domain/statemachine/workflow/StateMachineWorkflowComponentFactoryBootstrap.scala`
- `target/scala-3.3.8/src_managed/main/scala/domain/statemachine/workflow/LifecycleWorkflowStateMachineWorkflow1.scala`

[`WorkflowLifecycleAbiGenerationSpec`](../../src/test/scala/cozy/modeler/WorkflowLifecycleAbiGenerationSpec.scala)
pairs with this contract. It specifies both repeated public routes, canonical
projection equality, complete source/optional/order/precision/escaping
behavior, alias spelling, mixed collection dominance and legacy preservation.
The four independent
[v2 baseline products](../../src/test/resources/modeler/workflow-lifecycle-v2-baseline/)
are original product bytes; existing v1 product pins remain separate.

Normal public outputs use `target/test-generated/workflow-lifecycle-normal`
and `target/test-generated/workflow-lifecycle-value`. The mixed behavior emits
actual products to `target/test-generated/workflow-lifecycle-compile`, with
ordered members `LegacyWorkflow`, `PolicyWorkflow`, `LifecycleEdgeWorkflow`,
`WaitOnlyWorkflow`, `DeadlineOnlyWorkflow`, `CancellationOnlyWorkflow`.
`WorkflowLifecycleAbiConsumerProbe.scala` beside those products is a typed
test consumer, not a generated Cozy artifact. Parent-owned Scala 3.3.8
compilation checks the actual emitted products, old constructor signatures,
closed matches and typed Long/Signal access. P67-S672-VAL-009 successfully
compiled these ten actual sources with Scala 3.3.8; focused producer validation
P67-S672-VAL-007 passed 195 tests and independent producer review passed.

Workflow identity/version, root/definition location, State identity, wait
identity and declaration location provide durable static correlation.
Run/continuation IDs, due timestamps, revisions, stored history, admission,
scheduling, signal delivery, cooperative cancellation and runtime
suspension/resumption remain CNCF Phase 82 responsibilities. Generated
ComponentFactory metadata still wraps descriptors directly and selects no
provider. Cozy adds no runtime adapter, timer execution or persistence.
See the paired [lowering design](../design/workflow-scheduling-lifecycle-abi-lowering.md).
