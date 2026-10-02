# Workflow Failure and Execution Safety ABI Contract

Semantic role: Stable Semantics (normative).
Current status: S68.1 SOURCE accepted; S68.2 implementation and executable specification authored; focused producer validation92/92 and actual generated Scala3.3.8 normal/value/mixed 4/4/10 source groups passed; independent producer review and native Step commit pending. S68.3 remains planned.
Owner: Cozy Workflow ABI producer, Phase68 parent.
Update rule: update together with the paired lowering design and executable specification; advance acceptance only with parent-owned native evidence.
Phase: [68](../phase/phase-68.md), S68.2 ABI.
Updated: 2026-10-03.

## Collection selection and additive source compatibility

Selection applies once to the entire ordered Workflow collection. Every shared
ABI `VERSION`, JSON root `schemaVersion`, Scala descriptor `schemaVersion` and
bootstrap schema agrees; an old member in a mixed collection never downgrades.

| First matching collection condition | ABI | Bootstrap | Included optional declarations |
| --- | --- | --- | --- |
| Any failurePolicies nonempty or executionSafetyPolicy present | cozy.cml.statemachine-workflow-abi.v4 | cozy.cml.statemachine-workflow-bootstrap.v4 | invocation, lifecycle, waits, failure and safety |
| Any lifecyclePolicy present or waits nonempty | cozy.cml.statemachine-workflow-abi.v3 | cozy.cml.statemachine-workflow-bootstrap.v3 | invocation, lifecycle and waits |
| Any invocationPolicies nonempty | cozy.cml.statemachine-workflow-abi.v2 | cozy.cml.statemachine-workflow-bootstrap.v2 | invocation |
| Otherwise, including empty input | cozy.cml.statemachine-workflow-abi.v1 | cozy.cml.statemachine-workflow-bootstrap.v1 | none |

The producer adds constants `executionSafetySchemaVersion` and
`executionSafetyBootstrapSchemaVersion` with the v4 values above; all v1/v2/v3
constants remain. Existing product paths remain:
`target/scala-3.3.8/src_managed/main/scala/domain/statemachine/workflow/StateMachineWorkflowAbi.scala`,
`StateMachineWorkflowComponentFactoryBootstrap.scala` and each ordered
`<Identity>StateMachineWorkflow<ordinal>.scala` in that same directory, plus
`target/cozy/statemachine-workflow-abi.json`. Bootstrap directly references each
ordered member's `componentFactoryMetadata`; each member wraps its actual
descriptor in the existing `ComponentFactoryMetadata(workflow)`.

V4 preserves every previous WorkflowDescriptor label, type, order and default,
then appends after `waits`:

```scala
failurePolicies: Vector[FailurePolicy] = Vector.empty,
executionSafetyPolicy: Option[ExecutionSafetyPolicy] = None
```

Old seven-, eight- and ten-positional-argument source clients remain valid.
Binary case-class or unapply compatibility is not promised. ActionExecution,
context, continuation, Required SPI and ComponentFactoryMetadata receive no
fields. Without a v4 trigger the old generated declarations, arguments and JSON
keys remain absent and the original v1/v2/v3 bytes remain unchanged.

## Closed immutable Scala values

After existing invocation/lifecycle/wait declarations, v4 adds exactly:

```scala
sealed trait FailureClassification { def canonicalValue: String }
object FailureClassification {
  case object Retryable extends FailureClassification { val canonicalValue: String = "RETRYABLE" }
  case object Permanent extends FailureClassification { val canonicalValue: String = "PERMANENT" }
}
final case class FailureRule(identity: String, classification: FailureClassification, source: SourceIdentity)
final case class FailurePolicy(identity: String, target: InvocationTarget, actionIdentity: String, defaultClassification: FailureClassification, rules: Vector[FailureRule], source: SourceIdentity)
sealed trait DuplicateProtection { def canonicalValue: String }
object DuplicateProtection {
  case object NotRequired extends DuplicateProtection { val canonicalValue: String = "NOT_REQUIRED" }
  final case class Required(idempotencyKeyRef: String) extends DuplicateProtection { val canonicalValue: String = "REQUIRED" }
}
final case class ExecutionSafetyPolicy(logicalExecutionIdentityRef: String, duplicateProtection: DuplicateProtection, source: SourceIdentity)
```

Policies and rules retain source order. Targets retain declared Action or
Participant kind and exact spelling separately from canonical `actionIdentity`.
The producer performs no further alias resolution. Rules retain exact trimmed,
case-sensitive provider-neutral failure identity strings. Classification is the
closed Retryable/Permanent alternative, including each explicitly declared
default. Default-only policies have an empty rule vector. Safety absence is
None; NotRequired contains no key data; Required retains its exact opaque ref.

## Canonical compact JSON

Root field order is `schemaVersion,workflows`. V4 Workflow field order is
`identity,version,source,states,actions,requiredSpi,invocationPolicies,lifecyclePolicy,waits,failurePolicies,executionSafetyPolicy`.
Existing field meanings and nested orders are retained from prior contracts.
FailurePolicy field order is `identity,target,actionIdentity,defaultClassification,rules,source`:

```json
{"identity":"capture-failure","target":{"kind":"PARTICIPANT","identity":"capture-payment-capability"},"actionIdentity":"capture-payment","defaultClassification":"PERMANENT","rules":[{"identity":"transient.payment","classification":"RETRYABLE","source":{"line":null}},{"identity":"payment.rejected","classification":"PERMANENT","source":{"line":null}}],"source":{"line":null}}
```

The source-free example illustrates shape; located CML uses its actual heading
locations for all emitted line integers. Targets use ordered `kind,identity`, with ACTION or
PARTICIPANT. Rule fields are ordered `identity,classification,source`. The only
classification strings are RETRYABLE/PERMANENT. Missing policies are `[]`;
default-only rules are `[]`, without any manufactured rule.

Safety fields are ordered `logicalExecutionIdentityRef,duplicateProtection,source`:

```json
{"logicalExecutionIdentityRef":"order-run","duplicateProtection":{"kind":"REQUIRED","idempotencyKeyRef":"order-start"},"source":{"line":null}}
{"logicalExecutionIdentityRef":"order-run","duplicateProtection":{"kind":"NOT_REQUIRED","idempotencyKeyRef":null},"source":{"line":null}}
```

Duplicate-protection fields are always `kind,idempotencyKeyRef`; NotRequired's
key is null. Missing safety is null. Every source object is exactly
`{"line":<actual integer or null>}`. Root/definition correlation and existing
state/action/SPI/invocation/lifecycle/wait sources retain their actual locations.
V4 always includes inherited invocation/lifecycle/waits, emitting `[]`, null,
`[]` respectively when absent.

## Lossless values and independent boundaries

All opaque strings preserve quotes, backslashes, newline, carriage return, tab,
ISO controls and Unicode with established escaping. There is no trimming,
casing, sorting, normalization or interpretation at this lowering boundary.
All inherited Long values remain exact decimal JSON integers and Scala `L`
literals, including Long.MaxValue and zero. Values are never converted to Double
or inferred. Source None remains null/None without invented locations.

Logical identity/key references denote runtime-supplied values, not actual IDs
or generated keys. Failure classification creates no retry budget, backoff or
jitter. Action idempotency, compensation and UnitOfWork remain independent.
Cozy exports static immutable metadata without exception-class serialization,
storage, deduplication, dispatch or admission enforcement.

CNCF83 owns exact-rule-else-explicit-default interpretation; PERMANENT excludes
retry and RETRYABLE is eligible only within an existing explicit budget. CNCF83
owns runtime admission and dedup, rejecting admission when REQUIRED cannot be
honored, stable logical identity/key across retry/redelivery and distinct new
logical executions, independent physical attempt correlation, persisted
histories and integration. CNCF runtime completion is not a Cozy acceptance gate.

## Evidence and actual compiler inputs

The [executable ABI specification](../../src/test/scala/cozy/modeler/WorkflowExecutionSafetyAbiGenerationSpec.scala)
projects the [tracked safety fixture](../../src/test/resources/modeler/workflow-failure-execution-safety-policy.cml)
through `modeler-scala` and `modeler-scala-value`, each twice, comparing four
actual products and independent structured expected JSON. It covers optional
source variations, aliases, ordered policies/rules, programmatic escaped and
source-free edges, maximum Long/zero values and ScalaCheck opaque strings.
The [immutable original v3 baseline provenance](../../src/test/resources/modeler/workflow-execution-safety-v3-baseline/provenance.md)
records genuine original pre-edit bytes from commit
`cea56ab50417d3398a5f4399d2e2d9d0aa751de3`; both repeated public routes compare
all four byte vectors. Existing v2 baseline and v1 pins remain active. A
same-current-producer comparison alone is not original legacy evidence.

Actual behavior specs write source groups under
`target/test-generated/workflow-execution-safety-normal`, `...-value` and
`...-compile`. Each contains the actual ABI/bootstrap and a typed
`WorkflowExecutionSafetyAbiConsumerProbe.scala` in the generated Scala directory.
Normal/value groups each contain SafetyWorkflowStateMachineWorkflow1 (four
sources total). Mixed contains seven members in order: LegacyWorkflow1,
PolicyWorkflow2, LifecycleWorkflow3, SafetyWorkflow4, FailureOnlyWorkflow5,
NotRequiredWorkflow6, SafetyEdgeWorkflow7, with StateMachineWorkflow inserted
between identity and ordinal (ten sources total).

Parent compilation uses three separate source groups sequentially in one
registered serialized SBT invocation, Scala3.3.8 and matching scala3-library_3
only, dependencyOverrides Nil. The probes demonstrate old constructor arities,
new immutable types, source labels, exhaustive closed alternatives, exact refs,
ordered rules and inherited Long fields from actual members. Parent-admitted
P68-S682-VAL-002 passed focused producer validation92/92 across seven completed
suites, zero aborted suites and zero failed, canceled, ignored or pending tests,
including relevant Scala2.12.18 Test/compile. Parent-admitted P68-S682-VAL-003
compiled the actual Scala3.3.8 normal/value/mixed 4/4/10 source groups in one
serial invocation, with native .class/.tasty outputs for all eighteen sources
and three separate analyses. Both records report SBT/wrapper exit0 and
`lock=released`. Independent producer review, Step acceptance and native Step
commit remain pending.

Authorities: [accepted source contract](workflow-failure-execution-safety-source-contract.md),
[source IR](../../src/main/scala/cozy/modeler/WorkflowExecutionSafetyPolicy.scala),
[WorkflowDefinition](../../src/main/scala/cozy/modeler/CompositeStateMachineDefinition.scala),
[paired ABI lowering](../design/workflow-failure-execution-safety-abi-lowering.md),
[v1 contract](statemachine-workflow-generated-abi-contract.md),
[v2 contract](workflow-retry-timeout-abi-contract.md),
[v3 contract](workflow-scheduling-lifecycle-abi-contract.md).
