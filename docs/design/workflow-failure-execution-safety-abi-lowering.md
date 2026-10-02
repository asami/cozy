# Workflow Failure and Execution Safety ABI Lowering

Semantic role: Stable Semantics (frozen design).
Current status: S68.1 SOURCE accepted after source353/353, P68-S681-REVIEW-001 revision1 PASS and native commit 0a8062750a390343f7339fa4a1af8c02a6e4a8fd; S68.2 ABI accepted after producer92/92, actual Scala3.3.8 normal/value/mixed 4/4/10 compilation, P68-S682-REVIEW-001 revision1 PASS and native commit 3e2c7605f343e7eef75bf8166f98f1c6e0d89ac4. S68.3 [CNCF83 handoff](../phase/phase-68-cncf-handoff.md) authored; parent static verification, medium Step review and native Step commit pending. PHASE-68 remains active; sole full Phase review, final full suite and release pending.
Owner: Cozy Workflow ABI lowering, Phase68 parent.
Update rule: synchronize lowering with the normative ABI/source contracts and executable specification; acceptance follows parent-owned native evidence.
Phase: [68](../phase/phase-68.md), S68.2 ABI.
Updated: 2026-10-03.

## One additive producer boundary

[StateMachineWorkflowAbiGenerator](../../src/main/scala/cozy/modeler/StateMachineWorkflowAbiGenerator.scala)
projects the accepted immutable [failure/safety IR](../../src/main/scala/cozy/modeler/WorkflowExecutionSafetyPolicy.scala)
and [WorkflowDefinition](../../src/main/scala/cozy/modeler/CompositeStateMachineDefinition.scala).
The [source contract](../spec/workflow-failure-execution-safety-source-contract.md)
and [source lowering](workflow-failure-execution-safety-source-lowering.md)
own normalization; this producer does not resolve aliases again. Unchanged
ScalaGenerator and Modeler feed both public normal/value Scala routes.

The complete ordered collection selects v4 when any failurePolicies is nonempty
or executionSafetyPolicy is present. V4 enables all v3 lifecycle/wait and v2
invocation declarations even if absent on every member. Otherwise the unchanged
priority is lifecycle/waits → v3, invocation → v2, neither (including empty) →
v1. New `executionSafetySchemaVersion` and
`executionSafetyBootstrapSchemaVersion` supplement the retained six legacy
constants. One selection is threaded through existing private renderers; shared
VERSION, JSON root, every Scala descriptor and bootstrap agree.

Existing paths, direct ordered bootstrap references and metadata wrapping are
preserved. V4 appends closed FailureClassification, FailureRule, FailurePolicy,
DuplicateProtection and ExecutionSafetyPolicy declarations after prior types.
WorkflowDescriptor appends defaulted `Vector[FailurePolicy]` and
`Option[ExecutionSafetyPolicy]` after waits. Seven/eight/ten-argument source
constructors remain usable; binary/unapply compatibility is not claimed.
Without v4, every new declaration/argument/key is omitted, retaining old bytes.
No generic rendering framework, helper source file, dependency, reflection or
runtime mechanism is introduced.

## Ordered lossless projection

Existing quote/option/vector/source and InvocationTarget rendering is reused.
Failure policy order is identity,target,actionIdentity,defaultClassification,
rules,source; rule order is identity,classification,source. Scala uses closed
Retryable/Permanent alternatives; compact JSON uses RETRYABLE/PERMANENT.
Targets remain kind,identity with ACTION/PARTICIPANT and exact declared spelling
separate from canonical actionIdentity. Rules remain exact, case-sensitive,
provider-neutral logical identities. Default-only rules remain empty.

Safety order is logicalExecutionIdentityRef,duplicateProtection,source;
protection order is kind,idempotencyKeyRef. REQUIRED preserves the exact opaque
key; NOT_REQUIRED emits null and carries no Scala key. Absent policies/safety
emit []/null and Vector.empty/None. Inherited v4 invocation/lifecycle/waits
absence is []/null/[] and Vector.empty/None/Vector.empty. Root JSON remains
schemaVersion,workflows; Workflow adds failurePolicies,executionSafetyPolicy
after waits. Every previous field and nested order remains unchanged.

Source correlation retains actual heading lines or None; JSON source objects
remain line:<integer or null>. No locations are manufactured. Quotes,
backslashes, newline, carriage return, tab, ISO controls and Unicode retain
their exact values with established escapes. Long maxima and zero use decimal
integer JSON and Scala L literals without floating point, inference, sorting,
trimming, casing or normalization. See the full
[normative ABI shapes and type contract](../spec/workflow-failure-execution-safety-abi-contract.md)
and prior [v1](../spec/statemachine-workflow-generated-abi-contract.md),
[v2](../spec/workflow-retry-timeout-abi-contract.md) and
[v3](../spec/workflow-scheduling-lifecycle-abi-contract.md) contracts.

## Executable producer and source compatibility evidence

The [new executable specification](../../src/test/scala/cozy/modeler/WorkflowExecutionSafetyAbiGenerationSpec.scala)
projects the [full tracked CML](../../src/test/resources/modeler/workflow-failure-execution-safety-policy.cml)
through both public routes twice and compares all four actual products.
Independent expected structured JSON verifies complete old/new data and ordered
fields. Real CML tables cover either v4 trigger, default-only rules, REQUIRED,
NOT_REQUIRED, combined values, aliases and ordered distinct targets/rules.
Programmatic accepted-IR copies cover strings outside heading grammar, source
None and inherited Long extrema; ScalaCheck samples are memory-only.

The genuine original four v3 products are copied unchanged from the parent's
pre-edit capture, with [native provenance](../../src/test/resources/modeler/workflow-execution-safety-v3-baseline/provenance.md)
at original commit `cea56ab50417d3398a5f4399d2e2d9d0aa751de3`. Expected legacy
bytes are never regenerated using the edited producer. Both repeated public
routes compare exact byte vectors. Existing independent v2 resources/v1 pins
remain active in the focused accumulator. Current-producer field comparison
is only preservation evidence, never the original-output oracle.

Actual behavior specs write compiler inputs under
`target/test-generated/workflow-execution-safety-{normal,value,compile}`.
Normal/value each include actual ABI/bootstrap/SafetyWorkflowStateMachineWorkflow1
and their route-specific typed probe. Mixed includes actual ABI/bootstrap,
LegacyWorkflowStateMachineWorkflow1, PolicyWorkflowStateMachineWorkflow2,
LifecycleWorkflowStateMachineWorkflow3, SafetyWorkflowStateMachineWorkflow4,
FailureOnlyWorkflowStateMachineWorkflow5, NotRequiredWorkflowStateMachineWorkflow6,
SafetyEdgeWorkflowStateMachineWorkflow7 and its typed probe, plus the sidecar.
Every file resides beneath the established generated Scala root; probes are test
consumers, not Cozy-generated products. The mixed ABI equals both public v4 ABIs.

The probes construct old 7/8/10-argument descriptors, inspect defaulted new
fields, construct all new immutable variants and earlier policy/wait types,
exhaustively match classifications/protection/targets and read actual refs,
source Option[Int], ordered rules and deadline/wait/retry/timeout Long data.
Parent compiles the three separate groups (4/4/10 sources) in one registered
serialized SBT invocation using Scala3.3.8, matching standard library only and
dependencyOverrides Nil, with separate target/phase68-generated-compile group
class/analysis directories. No handwritten replacement ABI or stale compilation
stands in for those inputs. Parent-admitted P68-S682-VAL-002 passed focused
producer validation92/92 across seven completed suites, zero aborted suites and
zero failed, canceled, ignored or pending tests, including relevant
Scala2.12.18 Test/compile. Parent-admitted P68-S682-VAL-003 compiled the actual
Scala3.3.8 normal/value/mixed 4/4/10 source groups in one serial invocation,
with native .class/.tasty outputs for all eighteen sources and three separate
analyses. Both records report SBT/wrapper exit0 and `lock=released`.
P68-S682-REVIEW-001 revision1 PASS and native Step commit
`3e2c7605f343e7eef75bf8166f98f1c6e0d89ac4` record S68.2 acceptance; S68.3 parent
static verification, review and native Step commit remain pending.

## Receiver responsibility

Cozy supplies static immutable contracts. Action idempotency, compensation and
UnitOfWork remain independent. It supplies no exception class serialization,
implicit retry/backoff/jitter, identity/key generation, storage, dedup, admission
or dispatch. CNCF83 interprets exact-rule-else-explicit-default classification,
excludes permanent retry and admits retryable only within explicit existing
budget. CNCF83 must enforce REQUIRED admission protection, maintain stable
logical identity/key through retry/redelivery and distinct new executions,
correlate physical attempts, persist histories and integrate the receiver.
Those runtime mechanisms are recipient work, not Cozy completion gates. S68.3
has authored the [same-Phase handoff](../phase/phase-68-cncf-handoff.md); parent
static verification, review and native Step commit remain pending.
